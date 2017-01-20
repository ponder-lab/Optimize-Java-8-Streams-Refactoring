package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

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

@SuppressWarnings("restriction")
public class StreamAnalysisVisitor extends ASTVisitor {
	private Set<Stream> streamSet = new HashSet<>();

	private static final Logger logger = Logger.getLogger("edu.cuny.hunter.streamrefactoring");

	public StreamAnalysisVisitor() {
		super();
	}

	public StreamAnalysisVisitor(boolean visitDocTags) {
		super(visitDocTags);
	}

	@Override
	public boolean visit(MethodInvocation node) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		ITypeBinding returnType = methodBinding.getReturnType();
		boolean returnTypeImplementsBaseStream = implementsBaseStream(returnType);

		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		boolean declaringClassImplementsBaseStream = implementsBaseStream(declaringClass);

		// java.util.stream.BaseStream is the top-level interface for all
		// streams. Make sure we don't include intermediate operations.
		if (returnTypeImplementsBaseStream
				&& !(!JdtFlags.isStatic(methodBinding) && declaringClassImplementsBaseStream)) {
			Stream stream = new Stream(node);
			inferStreamExecution(stream, node);
			try {
				inferStreamOrdering(stream, node);
				// TODO: Need to single out some exceptions here for errors.
				// Exceptions should be converted to RefactoringStatuses and
				// associated with the Stream.
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			this.getStreamSet().add(stream);
		}

		return super.visit(node);
	}

	private static void inferStreamOrdering(Stream stream, MethodInvocation node) throws IOException, CoreException,
			ClassHierarchyException, InvalidClassFileException, InconsistentPossibleStreamSourceOrderingException,
			NoniterablePossibleStreamSourceException, NoninstantiablePossibleStreamSourceException {
		ITypeBinding expressionTypeBinding = node.getExpression().resolveTypeBinding();
		String expressionTypeQualifiedName = expressionTypeBinding.getErasure().getQualifiedName();
		IMethodBinding methodBinding = node.resolveMethodBinding();

		if (JdtFlags.isStatic(methodBinding)) {
			// static methods returning unordered streams.
			if (expressionTypeQualifiedName.equals("java.util.stream.Stream")) {
				String methodIdentifier = getMethodIdentifier(methodBinding);
				if (methodIdentifier.equals("generate(java.util.function.Supplier)"))
					stream.setOrdering(StreamOrdering.UNORDERED);
			} else
				stream.setOrdering(StreamOrdering.ORDERED);
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
			MethodDeclaration enclosingMethodDeclaration = (MethodDeclaration) ASTNodes.getParent(node,
					MethodDeclaration.class);

			JDTIdentityMapper mapper = new JDTIdentityMapper(JavaSourceAnalysisScope.SOURCE,
					enclosingMethodDeclaration.getAST());
			MethodReference methodRef = mapper.getMethodRef(enclosingMethodDeclaration.resolveBinding());

			if (methodRef == null)
				throw new IllegalStateException(
						"Could not get method reference for: " + enclosingMethodDeclaration.getName());

			com.ibm.wala.classLoader.IMethod method = classHierarchy.resolveMethod(methodRef);
			IR ir = cache.getSSACache().findOrCreateIR(method, Everywhere.EVERYWHERE, options.getSSAOptions());

			if (ir == null)
				throw new IllegalStateException("IR is null for: " + method);

			int valueNumber = getUseValueNumberForInvocation(node, ir);
			TypeInference inference = TypeInference.make(ir, false);
			Set<TypeAbstraction> possibleTypes = getPossibleTypes(valueNumber, inference);

			// Possible types: check each one.
			StreamOrdering ordering = inferStreamOrdering(possibleTypes);
			stream.setOrdering(ordering);
		}
	}

	private static StreamOrdering inferStreamOrdering(Set<TypeAbstraction> possibleStreamSourceTypes)
			throws InconsistentPossibleStreamSourceOrderingException, NoniterablePossibleStreamSourceException,
			NoninstantiablePossibleStreamSourceException {
		StreamOrdering ret = null;

		for (TypeAbstraction typeAbstraction : possibleStreamSourceTypes) {
			StreamOrdering ordering = inferStreamOrdering(typeAbstraction);

			if (ret == null)
				ret = ordering;
			else if (ret != ordering)
				throw new InconsistentPossibleStreamSourceOrderingException(
						ret + " does not match " + ordering + " for type: " + typeAbstraction + ".");
		}

		return ret;
	}

	private static StreamOrdering inferStreamOrdering(TypeAbstraction typeAbstraction)
			throws NoniterablePossibleStreamSourceException, NoninstantiablePossibleStreamSourceException {
		TypeReference typeReference = typeAbstraction.getTypeReference();
		String binaryName = getBinaryName(typeReference);

		return inferStreamOrdering(binaryName);
	}

	public static StreamOrdering inferStreamOrdering(String className)
			throws NoniterablePossibleStreamSourceException, NoninstantiablePossibleStreamSourceException {
		try {
			Class<?> clazz = Class.forName(className);

			// is it an Iterable?
			if (Iterable.class.isAssignableFrom(clazz)) {
				// is it instantiable?
				if (!clazz.isInterface()) {
					Iterable<?> instance = null;
					try {
						instance = (Iterable<?>) clazz.newInstance();
					} catch (InstantiationException e) {
						throw new NoninstantiablePossibleStreamSourceException(clazz + " cannot be instantiated: " + e.getCause(),
								e);
					} catch (IllegalAccessException e) {
						throw new NoninstantiablePossibleStreamSourceException(
								clazz + " cannot be instantiated due to an access exception: " + e, e);
					}
					boolean ordered = instance.spliterator().hasCharacteristics(Spliterator.ORDERED);

					// FIXME: What if there is something under this that is
					// ordered?
					if (!ordered)
						return StreamOrdering.UNORDERED;
					else
						// FIXME: A java.util.Set may actually not be ordered.
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

	private static String getBinaryName(TypeReference typeReference) {
		TypeName name = typeReference.getName();
		String slashToDot = StringStuff.slashToDot(name.getPackage().toString() + "." + name.getClassName().toString());
		return slashToDot;
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

	private static void inferStreamExecution(Stream stream, MethodInvocation node) {
		String methodIdentifier = getMethodIdentifier(node.resolveMethodBinding());

		if (methodIdentifier.equals("parallelStream()"))
			stream.setExecutionMode(StreamExecutionMode.PARALLEL);
		else
			stream.setExecutionMode(StreamExecutionMode.SEQUENTIAL);
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

	private static boolean implementsBaseStream(ITypeBinding type) {
		Set<ITypeBinding> implementedInterfaces = getImplementedInterfaces(type);
		return implementedInterfaces.stream()
				.anyMatch(i -> i.getErasure().getQualifiedName().equals("java.util.stream.BaseStream"));
	}

	private static Set<ITypeBinding> getImplementedInterfaces(ITypeBinding type) {
		Set<ITypeBinding> ret = new HashSet<>();

		if (type.isInterface())
			ret.add(type);

		ret.addAll(getAllInterfaces(type));
		return ret;
	}

	private static Set<ITypeBinding> getAllInterfaces(ITypeBinding type) {
		Set<ITypeBinding> ret = new HashSet<>();
		ITypeBinding[] interfaces = type.getInterfaces();
		ret.addAll(Arrays.asList(interfaces));

		for (ITypeBinding interfaceBinding : interfaces)
			ret.addAll(getAllInterfaces(interfaceBinding));

		return ret;
	}

	public Set<Stream> getStreamSet() {
		return streamSet;
	}
}
