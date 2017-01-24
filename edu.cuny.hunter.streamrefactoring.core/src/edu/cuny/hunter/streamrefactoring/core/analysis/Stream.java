package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
import org.osgi.framework.FrameworkUtil;

import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.JDTIdentityMapper;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.client.AbstractAnalysisEngine;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.PhiValue;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.Value;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.StringStuff;

import edu.cuny.hunter.streamrefactoring.core.utils.Util;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

/**
 * An abstract notion of a stream in memory.
 * 
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi
 *         Khatchadourian</a>
 *
 */
@SuppressWarnings("restriction")
public class Stream {
	private static final String PLUGIN_ID = FrameworkUtil.getBundle(Stream.class).getSymbolicName();

	private final MethodInvocation creation;

	private final MethodDeclaration enclosingMethodDeclaration;

	private StreamExecutionMode executionMode;

	private StreamOrdering ordering;

	private RefactoringStatus status = new RefactoringStatus();

	private static Objenesis objenesis = new ObjenesisStd();

	private static final Logger logger = Logger.getLogger("edu.cuny.hunter.streamrefactoring");

	public Stream(MethodInvocation streamCreation)
			throws ClassHierarchyException, IOException, CoreException, InvalidClassFileException {
		this.creation = streamCreation;
		this.enclosingMethodDeclaration = (MethodDeclaration) ASTNodes.getParent(this.getCreation(),
				ASTNode.METHOD_DECLARATION);
		this.inferExecution();
		try {
			this.inferOrdering();
		} catch (InconsistentPossibleStreamSourceOrderingException e) {
			logger.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(streamCreation, PreconditionFailure.INCONSISTENT_POSSIBLE_STREAM_SOURCE_ORDERING,
					"Stream: " + streamCreation + " has inconsistent possible source orderings.");
		} catch (NoniterablePossibleStreamSourceException e) {
			logger.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(streamCreation, PreconditionFailure.NON_ITERABLE_POSSIBLE_STREAM_SOURCE,
					"Stream: " + streamCreation + " has a non-iterable possible source.");
		} catch (NoninstantiablePossibleStreamSourceException e) {
			logger.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(streamCreation, PreconditionFailure.NON_INSTANTIABLE_POSSIBLE_STREAM_SOURCE,
					"Stream: " + streamCreation + " has a non-instantiable possible source.");
		} catch (CannotDetermineStreamOrderingException e) {
			logger.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(streamCreation, PreconditionFailure.NON_DETERMINABLE_STREAM_SOURCE_ORDERING,
					"Cannot determine ordering of source for stream: " + streamCreation + ".");
		}
	}

	private void addStatusEntry(MethodInvocation streamCreation, PreconditionFailure failure, String message) {
		CompilationUnit compilationUnit = (CompilationUnit) ASTNodes.getParent(streamCreation,
				ASTNode.COMPILATION_UNIT);
		ICompilationUnit compilationUnit2 = (ICompilationUnit) compilationUnit.getJavaElement();
		RefactoringStatusContext context = JavaStatusContext.create(compilationUnit2, streamCreation);
		this.getStatus().addEntry(RefactoringStatus.ERROR, message, context, PLUGIN_ID, failure.getCode(), this);
	}

	private void inferExecution() {
		String methodIdentifier = getMethodIdentifier(this.getCreation().resolveMethodBinding());

		if (methodIdentifier.equals("parallelStream()"))
			this.setExecutionMode(StreamExecutionMode.PARALLEL);
		else
			this.setExecutionMode(StreamExecutionMode.SEQUENTIAL);
	}

	private void inferOrdering() throws IOException, CoreException, ClassHierarchyException, InvalidClassFileException,
			InconsistentPossibleStreamSourceOrderingException, NoniterablePossibleStreamSourceException,
			NoninstantiablePossibleStreamSourceException, CannotDetermineStreamOrderingException {
		ITypeBinding expressionTypeBinding = this.getCreation().getExpression().resolveTypeBinding();
		String expressionTypeQualifiedName = expressionTypeBinding.getErasure().getQualifiedName();
		IMethodBinding methodBinding = this.getCreation().resolveMethodBinding();

		if (JdtFlags.isStatic(methodBinding)) {
			// static methods returning unordered streams.
			if (expressionTypeQualifiedName.equals("java.util.stream.Stream")) {
				String methodIdentifier = getMethodIdentifier(methodBinding);
				if (methodIdentifier.equals("generate(java.util.function.Supplier)"))
					this.setOrdering(StreamOrdering.UNORDERED);
			} else
				this.setOrdering(StreamOrdering.ORDERED);
		} else { // instance method.
			IJavaElement javaElement = methodBinding.getJavaElement();
			IJavaProject javaProject = javaElement.getJavaProject();
			AbstractAnalysisEngine<InstanceKey> engine = new EclipseProjectAnalysisEngine<InstanceKey>(javaProject);
			// FIXME: [RK] Inefficient to build this every time, I'd imagine.
			engine.buildAnalysisScope();
			IClassHierarchy classHierarchy = engine.buildClassHierarchy();
			AnalysisOptions options = new AnalysisOptions();
			// options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
			AnalysisCache cache = new AnalysisCache();

			// get the IR for the enclosing method.
			JDTIdentityMapper mapper = new JDTIdentityMapper(JavaSourceAnalysisScope.SOURCE,
					getEnclosingMethodDeclaration().getAST());
			MethodReference methodRef = mapper.getMethodRef(getEnclosingMethodDeclaration().resolveBinding());

			if (methodRef == null)
				throw new IllegalStateException(
						"Could not get method reference for: " + getEnclosingMethodDeclaration().getName());

			com.ibm.wala.classLoader.IMethod method = classHierarchy.resolveMethod(methodRef);
			IR ir = cache.getSSACache().findOrCreateIR(method, Everywhere.EVERYWHERE, options.getSSAOptions());

			if (ir == null)
				throw new IllegalStateException("IR is null for: " + method);

			int valueNumber = getUseValueNumberForInvocation(this.getCreation(), ir);
			TypeInference inference = TypeInference.make(ir, false);
			Set<TypeAbstraction> possibleTypes = getPossibleTypes(valueNumber, inference);

			// Possible types: check each one.
			StreamOrdering ordering = inferStreamOrdering(possibleTypes);
			this.setOrdering(ordering);
		}
	}

	private static Set<TypeAbstraction> getPossibleTypes(int valueNumber, TypeInference inference) {
		Set<TypeAbstraction> ret = new HashSet<>();
		Value value = inference.getIR().getSymbolTable().getValue(valueNumber);

		if (value instanceof PhiValue) {
			// multiple possible types.
			PhiValue phiValue = (PhiValue) value;
			SSAPhiInstruction phiInstruction = phiValue.getPhiInstruction();
			int numberOfUses = phiInstruction.getNumberOfUses();
			// get the possible types for each use.
			for (int i = 0; i < numberOfUses; i++) {
				int use = phiInstruction.getUse(i);
				Set<TypeAbstraction> possibleTypes = getPossibleTypes(use, inference);
				ret.addAll(possibleTypes);
			}
		} else {
			// one one possible type.
			ret.add(inference.getType(valueNumber));
		}

		return ret;
	}

	private static int getUseValueNumberForInvocation(MethodInvocation node, IR ir) throws InvalidClassFileException {
		IBytecodeMethod method = (IBytecodeMethod) ir.getMethod();
		SimpleName methodName = node.getName();

		for (Iterator<SSAInstruction> it = ir.iterateNormalInstructions(); it.hasNext();) {
			SSAInstruction instruction = it.next();
			System.out.println(instruction);

			int lineNumberFromIR = getLineNumberFromIR(method, instruction);
			int lineNumberFromAST = getLineNumberFromAST(methodName);

			if (lineNumberFromIR == lineNumberFromAST) {
				// lines from the AST and the IR match. Let's dive a little
				// deeper to be more confident of the correspondence.
				if (instruction.hasDef() && instruction.getNumberOfDefs() == 2) {
					if (instruction instanceof SSAInvokeInstruction) {
						SSAInvokeInstruction invokeInstruction = (SSAInvokeInstruction) instruction;
						TypeReference declaredTargetDeclaringClass = invokeInstruction.getDeclaredTarget()
								.getDeclaringClass();
						if (getBinaryName(declaredTargetDeclaringClass)
								.equals(node.getExpression().resolveTypeBinding().getBinaryName())) {
							MethodReference callSiteDeclaredTarget = invokeInstruction.getCallSite()
									.getDeclaredTarget();
							// FIXME: This matching needs much work.
							if (callSiteDeclaredTarget.getName().toString()
									.equals(node.resolveMethodBinding().getName())) {
								return invokeInstruction.getUse(0);
							}
						}
					} else
						logger.warning("Instruction: " + instruction + " is not an SSAInstruction.");
				} else
					logger.warning("Instruction: " + instruction + " has no definitions.");
			}
		}
		return -1;
	}

	private static int getLineNumberFromAST(SimpleName methodName) {
		CompilationUnit compilationUnit = (CompilationUnit) ASTNodes.getParent(methodName, ASTNode.COMPILATION_UNIT);
		int lineNumberFromAST = compilationUnit.getLineNumber(methodName.getStartPosition());
		return lineNumberFromAST;
	}

	private static int getLineNumberFromIR(IBytecodeMethod method, SSAInstruction instruction)
			throws InvalidClassFileException {
		int bytecodeIndex = method.getBytecodeIndex(instruction.iindex);
		int lineNumberFromIR = method.getLineNumber(bytecodeIndex);
		return lineNumberFromIR;
	}

	private static String getBinaryName(TypeReference typeReference) {
		TypeName name = typeReference.getName();
		String slashToDot = StringStuff.slashToDot(name.getPackage().toString() + "." + name.getClassName().toString());
		return slashToDot;
	}

	private static String getMethodIdentifier(IMethodBinding methodBinding) {
		IMethod method = (IMethod) methodBinding.getJavaElement();

		String methodIdentifier = null;
		try {
			methodIdentifier = Util.getMethodIdentifier(method);
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
		return methodIdentifier;
	}

	private static StreamOrdering inferStreamOrdering(Set<TypeAbstraction> possibleStreamSourceTypes)
			throws InconsistentPossibleStreamSourceOrderingException, NoniterablePossibleStreamSourceException,
			NoninstantiablePossibleStreamSourceException, CannotDetermineStreamOrderingException {
		StreamOrdering ret = null;

		for (TypeAbstraction typeAbstraction : possibleStreamSourceTypes) {
			if (typeAbstraction != TypeAbstraction.TOP) {
				StreamOrdering ordering = inferStreamOrdering(typeAbstraction);

				if (ret == null)
					ret = ordering;
				else if (ret != ordering)
					throw new InconsistentPossibleStreamSourceOrderingException(
							ret + " does not match " + ordering + " for type: " + typeAbstraction + ".");
			}
		}

		return ret;
	}

	private static StreamOrdering inferStreamOrdering(TypeAbstraction typeAbstraction)
			throws NoniterablePossibleStreamSourceException, NoninstantiablePossibleStreamSourceException,
			CannotDetermineStreamOrderingException {
		TypeReference typeReference = typeAbstraction.getTypeReference();
		String binaryName = getBinaryName(typeReference);

		return inferStreamOrdering(binaryName);
	}

	public static StreamOrdering inferStreamOrdering(String className) throws NoniterablePossibleStreamSourceException,
			NoninstantiablePossibleStreamSourceException, CannotDetermineStreamOrderingException {
		try {
			Class<?> clazz = Class.forName(className);

			// is it an Iterable?
			if (Iterable.class.isAssignableFrom(clazz)) {
				// is it instantiable?
				if (!clazz.isInterface()) {
					Iterable<?> instance = createInstance(clazz);
					boolean ordered;

					try {
						ordered = instance.spliterator().hasCharacteristics(Spliterator.ORDERED);
					} catch (Exception e) {
						// TODO: Can we use something other than reflection,
						// like static analysis? Also, it may be an abstract
						// class.
						// not able to determine ordering via reflection.
						throw new CannotDetermineStreamOrderingException(
								"Cannot determine stream ordering via reflection for: " + clazz + ": " + e, e);
					}

					// FIXME: What if there is something under this that is
					// ordered?
					if (!ordered)
						return StreamOrdering.UNORDERED;
					else
						return StreamOrdering.ORDERED;
				} else
					throw new NoninstantiablePossibleStreamSourceException(
							clazz + " cannot be instantiated because it is an interface.");
			} else
				throw new NoniterablePossibleStreamSourceException(clazz + " does not implement java.util.Iterable.");

		} catch (ClassNotFoundException e) {
			// TODO Not sure what we should do in this situation. What if we
			// can't instantiate the iterable? Is there another way to find out
			// this information? This could be a problem in third-party
			// container libraries. Also, what if we don't have the class in the
			// classpath?
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private static Iterable<?> createInstance(Class<?> clazz) throws NoninstantiablePossibleStreamSourceException {
		try {
			return (Iterable<?>) clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			ObjectInstantiator<?> instantiator = objenesis.getInstantiatorOf(clazz);
			try {
				return (Iterable<?>) instantiator.newInstance();
			} catch (InstantiationError e2) {
				throw new NoninstantiablePossibleStreamSourceException(
						clazz + " cannot be instantiated: " + e2.getCause(), e2);
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Stream [streamCreation=");
		builder.append(creation);
		builder.append(", enclosingMethodDeclaration=");
		builder.append(enclosingMethodDeclaration);
		builder.append(", executionMode=");
		builder.append(executionMode);
		builder.append(", ordering=");
		builder.append(ordering);
		builder.append(", status=");
		builder.append(status);
		builder.append("]");
		return builder.toString();
	}

	public StreamExecutionMode getExecutionMode() {
		return executionMode;
	}

	protected void setExecutionMode(StreamExecutionMode executionMode) {
		this.executionMode = executionMode;
	}

	public StreamOrdering getOrdering() {
		return ordering;
	}

	protected void setOrdering(StreamOrdering ordering) {
		this.ordering = ordering;
	}

	public MethodInvocation getCreation() {
		return creation;
	}

	public MethodDeclaration getEnclosingMethodDeclaration() {
		return enclosingMethodDeclaration;
	}

	public RefactoringStatus getStatus() {
		return status;
	}

	public IMethod getEnclosingMethod() {
		return (IMethod) getEnclosingMethodDeclaration().resolveBinding().getJavaElement();
	}

	public IType getEnclosingType() {
		return (IType) getEnclosingMethodDeclaration().resolveBinding().getDeclaringClass().getJavaElement();
	}
}
