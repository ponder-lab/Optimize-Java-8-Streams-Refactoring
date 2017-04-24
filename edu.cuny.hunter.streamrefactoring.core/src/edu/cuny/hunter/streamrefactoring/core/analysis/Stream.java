package edu.cuny.hunter.streamrefactoring.core.analysis;

import static edu.cuny.hunter.streamrefactoring.core.safe.Util.instanceKeyCorrespondsWithInstantiationInstruction;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.BaseStream;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
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
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
import org.osgi.framework.FrameworkUtil;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.JDTIdentityMapper;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
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
import com.ibm.wala.util.CancelException;
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

	private static Map<IJavaProject, EclipseProjectAnalysisEngine<InstanceKey>> javaProjectToAnalysisEngineMap = new HashMap<>();

	private static Map<IJavaProject, IClassHierarchy> javaProjectToClassHierarchyMap = new HashMap<>();

	private static final Logger logger = Logger.getLogger("edu.cuny.hunter.streamrefactoring");

	private static Map<MethodDeclaration, IR> methodDeclarationToIRMap = new HashMap<>();

	private static Objenesis objenesis = new ObjenesisStd();

	private static final String PLUGIN_ID = FrameworkUtil.getBundle(Stream.class).getSymbolicName();

	public static void clearCaches() {
		javaProjectToClassHierarchyMap.clear();
		javaProjectToAnalysisEngineMap.clear();
		methodDeclarationToIRMap.clear();
		StreamStateMachine.clearCaches();
	}

	private static Object createInstance(Class<?> clazz) throws NoninstantiablePossibleStreamSourceException {
		try {
			return clazz.newInstance();
		} catch (InstantiationException | IllegalAccessException e) {
			ObjectInstantiator<?> instantiator = objenesis.getInstantiatorOf(clazz);
			try {
				return instantiator.newInstance();
			} catch (InstantiationError e2) {
				throw new NoninstantiablePossibleStreamSourceException(
						clazz + " cannot be instantiated: " + e2.getCause(), e2, clazz);
			}
		}
	}

	private static String getBinaryName(TypeReference typeReference) {
		TypeName name = typeReference.getName();
		String slashToDot = StringStuff.slashToDot(name.getPackage().toString() + "." + name.getClassName().toString());
		return slashToDot;
	}

	private static JDTIdentityMapper getJDTIdentifyMapper(ASTNode node) {
		return new JDTIdentityMapper(JavaSourceAnalysisScope.SOURCE, node.getAST());
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

	private static Set<TypeAbstraction> getPossibleTypes(int valueNumber, TypeInference inference) {
		Set<TypeAbstraction> ret = new HashSet<>();
		Value value = inference.getIR().getSymbolTable().getValue(valueNumber);

		// TODO: Should really be using a pointer analysis here rather than
		// reimplementing one using PhiValue.
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

	private static Spliterator<?> getSpliterator(Object instance, IMethod calledMethod)
			throws CannotExtractSpliteratorException {
		Spliterator<?> spliterator = null;

		if (instance instanceof Iterable) {
			spliterator = ((Iterable<?>) instance).spliterator();
		} else {
			// try to call the stream() method to get the spliterator.
			BaseStream<?, ?> baseStream = null;
			try {
				Method streamCreationMethod = instance.getClass().getMethod(calledMethod.getElementName());
				Object stream = streamCreationMethod.invoke(instance);

				if (stream instanceof BaseStream) {
					baseStream = (BaseStream<?, ?>) stream;
					spliterator = baseStream.spliterator();
				} else
					throw new CannotExtractSpliteratorException(
							"Returned object of type: " + stream.getClass() + " doesn't implement BaseStream.",
							stream.getClass());
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				throw new CannotExtractSpliteratorException(
						"Cannot extract the spliterator from object of type: " + instance.getClass(), e,
						instance.getClass());
			} finally {
				if (baseStream != null)
					baseStream.close();
			}
		}
		return spliterator;
	}

	private static StreamOrdering inferInitialStreamOrdering(Set<TypeAbstraction> possibleStreamSourceTypes,
			IMethod calledMethod)
			throws InconsistentPossibleStreamSourceOrderingException, NoniterablePossibleStreamSourceException,
			NoninstantiablePossibleStreamSourceException, CannotExtractSpliteratorException {
		StreamOrdering ret = null;

		for (TypeAbstraction typeAbstraction : possibleStreamSourceTypes) {
			if (typeAbstraction != TypeAbstraction.TOP) {
				StreamOrdering ordering = inferInitialStreamOrdering(typeAbstraction, calledMethod);

				if (ret == null)
					ret = ordering;
				else if (ret != ordering)
					throw new InconsistentPossibleStreamSourceOrderingException(
							ret + " does not match " + ordering + " for type: " + typeAbstraction + ".");
			}
		}

		return ret;
	}

	// TODO: Cache this?
	private static StreamOrdering inferInitialStreamOrdering(String className, IMethod calledMethod)
			throws NoniterablePossibleStreamSourceException, NoninstantiablePossibleStreamSourceException,
			CannotExtractSpliteratorException {
		try {
			Class<?> clazz = Class.forName(className);

			// is it instantiable?
			if (!isAbstractType(clazz)) {
				Object instance = createInstance(clazz);
				boolean ordered;

				Spliterator<?> spliterator = getSpliterator(instance, calledMethod);
				ordered = spliterator.hasCharacteristics(Spliterator.ORDERED);
				// TODO: Can we use something other than reflection,
				// like static analysis? Also, it may be an abstract
				// class.

				// FIXME: What if there is something under this that is
				// ordered? I guess this applies to both intra and
				// interprocedural analysis but more for the former.
				if (!ordered)
					return StreamOrdering.UNORDERED;
				else
					return StreamOrdering.ORDERED;
			} else
				throw new NoninstantiablePossibleStreamSourceException(
						clazz + " cannot be instantiated because it is an interface.", clazz);
		} catch (ClassNotFoundException e) {
			// TODO Not sure what we should do in this situation. What if we
			// can't instantiate the iterable? Is there another way to find out
			// this information? This could be a problem in third-party
			// container libraries. Also, what if we don't have the class in the
			// classpath?
			e.printStackTrace();
			throw new RuntimeException("Can't find: " + className, e);
		}
	}

	private static StreamOrdering inferInitialStreamOrdering(TypeAbstraction typeAbstraction, IMethod calledMethod)
			throws NoniterablePossibleStreamSourceException, NoninstantiablePossibleStreamSourceException,
			CannotExtractSpliteratorException {
		TypeReference typeReference = typeAbstraction.getTypeReference();
		String binaryName = getBinaryName(typeReference);

		return inferInitialStreamOrdering(binaryName, calledMethod);
	}

	private static boolean isAbstractType(Class<?> clazz) {
		// if it's an interface.
		if (clazz.isInterface())
			return true; // can't instantiate an interface.
		else if (Modifier.isAbstract(clazz.getModifiers()))
			return true; // can't instantiate an abstract type.
		else
			return false;
	}

	private final MethodInvocation creation;

	private final MethodDeclaration enclosingMethodDeclaration;

	private final TypeDeclaration enclosingTypeDeclaration;

	/**
	 * The execution mode derived from the declaration of the stream.
	 */
	private StreamExecutionMode initialExecutionMode;

	/**
	 * This should be the possible execution modes when the stream is consumed
	 * by a terminal operation. Does not include the initial mode.
	 */
	private Set<StreamExecutionMode> possibleExecutionModes = new HashSet<>();

	/**
	 * The ordering derived from the declaration of the stream.
	 */
	private StreamOrdering initialOrdering;

	/**
	 * This should be the ordering of the stream when it is consumed by a
	 * terimal operation.
	 */
	private Set<StreamOrdering> possibleOrderings = new HashSet<>();

	private RefactoringStatus status = new RefactoringStatus();

	public Stream(MethodInvocation streamCreation)
			throws ClassHierarchyException, IOException, CoreException, InvalidClassFileException {
		this.creation = streamCreation;
		this.enclosingTypeDeclaration = (TypeDeclaration) ASTNodes.getParent(this.getCreation(),
				ASTNode.TYPE_DECLARATION);
		this.enclosingMethodDeclaration = (MethodDeclaration) ASTNodes.getParent(this.getCreation(),
				ASTNode.METHOD_DECLARATION);

		this.inferInitialExecution();

		try {
			this.inferInitialOrdering();
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
			addStatusEntry(streamCreation, PreconditionFailure.NON_INSTANTIABLE_POSSIBLE_STREAM_SOURCE, "Stream: "
					+ streamCreation + " has a non-instantiable possible source with type: " + e.getSourceType() + ".");
		} catch (CannotExtractSpliteratorException e) {
			logger.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(streamCreation, PreconditionFailure.NON_DETERMINABLE_STREAM_SOURCE_ORDERING,
					"Cannot extract spliterator from type: " + e.getFromType() + " for stream: " + streamCreation
							+ ".");
		}

		try {
			new StreamStateMachine(this).start();
		} catch (PropertiesException | CancelException e) {
			logger.log(Level.SEVERE, "Error while building stream.", e);
			throw new RuntimeException(e);
		}
	}

	private void addStatusEntry(MethodInvocation streamCreation, PreconditionFailure failure, String message) {
		CompilationUnit compilationUnit = (CompilationUnit) ASTNodes.getParent(streamCreation,
				ASTNode.COMPILATION_UNIT);
		ICompilationUnit compilationUnit2 = (ICompilationUnit) compilationUnit.getJavaElement();
		RefactoringStatusContext context = JavaStatusContext.create(compilationUnit2, streamCreation);
		this.getStatus().addEntry(RefactoringStatus.ERROR, message, context, PLUGIN_ID, failure.getCode(), this);
	}

	EclipseProjectAnalysisEngine<InstanceKey> getAnalysisEngine() throws IOException, CoreException {
		IJavaProject javaProject = this.getCreationJavaProject();
		EclipseProjectAnalysisEngine<InstanceKey> engine = javaProjectToAnalysisEngineMap.get(javaProject);
		if (engine == null) {
			engine = new EclipseProjectAnalysisEngine<InstanceKey>(javaProject);

			if (engine != null)
				javaProjectToAnalysisEngineMap.put(javaProject, engine);
		}
		return engine;
	}

	IClassHierarchy getClassHierarchy() throws IOException, CoreException {
		IJavaProject javaProject = getCreationJavaProject();
		IClassHierarchy classHierarchy = javaProjectToClassHierarchyMap.get(javaProject);
		if (classHierarchy == null) {
			EclipseProjectAnalysisEngine<InstanceKey> engine = getAnalysisEngine();
			engine.buildAnalysisScope();
			classHierarchy = engine.buildClassHierarchy();

			if (classHierarchy != null)
				javaProjectToClassHierarchyMap.put(javaProject, classHierarchy);
		}

		return classHierarchy;
	}

	public MethodInvocation getCreation() {
		return creation;
	}

	private IJavaProject getCreationJavaProject() {
		return this.getCreation().resolveMethodBinding().getJavaElement().getJavaProject();
	}

	public IMethod getEnclosingEclipseMethod() {
		return (IMethod) getEnclosingMethodDeclaration().resolveBinding().getJavaElement();
	}

	public MethodDeclaration getEnclosingMethodDeclaration() {
		return enclosingMethodDeclaration;
	}

	private IR getEnclosingMethodIR() throws IOException, CoreException {
		IR ir = methodDeclarationToIRMap.get(getEnclosingMethodDeclaration());

		if (ir == null) {
			// get the IR for the enclosing method.
			com.ibm.wala.classLoader.IMethod resolvedMethod = getEnclosingWalaMethod();

			ir = Analysis.getCache().getSSACache().findOrCreateIR(resolvedMethod, Everywhere.EVERYWHERE,
					Analysis.getOptions().getSSAOptions());

			if (ir == null)
				throw new IllegalStateException("IR is null for: " + resolvedMethod);

			methodDeclarationToIRMap.put(getEnclosingMethodDeclaration(), ir);
		}
		return ir;
	}

	public com.ibm.wala.classLoader.IMethod getEnclosingWalaMethod() throws IOException, CoreException {
		IClassHierarchy classHierarchy = getClassHierarchy();
		MethodReference methodRef = getEnclosingMethodReference();
		com.ibm.wala.classLoader.IMethod resolvedMethod = classHierarchy.resolveMethod(methodRef);
		return resolvedMethod;
	}

	MethodReference getEnclosingMethodReference() {
		JDTIdentityMapper mapper = getJDTIdentifyMapper(getEnclosingMethodDeclaration());
		MethodReference methodRef = mapper.getMethodRef(getEnclosingMethodDeclaration().resolveBinding());

		if (methodRef == null)
			throw new IllegalStateException(
					"Could not get method reference for: " + getEnclosingMethodDeclaration().getName());
		return methodRef;
	}

	public IType getEnclosingType() {
		return (IType) getEnclosingMethodDeclaration().resolveBinding().getDeclaringClass().getJavaElement();
	}

	public TypeDeclaration getEnclosingTypeDeclaration() {
		return enclosingTypeDeclaration;
	}

	private TypeReference getEnclosingTypeReference() {
		JDTIdentityMapper mapper = getJDTIdentifyMapper(getEnclosingTypeDeclaration());
		TypeReference ref = mapper.getTypeRef(getEnclosingTypeDeclaration().resolveBinding());

		if (ref == null)
			throw new IllegalStateException(
					"Could not get type reference for: " + getEnclosingTypeDeclaration().getName());
		return ref;
	}

	public Set<StreamExecutionMode> getPossibleExecutionModes() {
		// if no other possible execution modes exist.
		if (possibleExecutionModes.isEmpty())
			// default to the initial execution mode.
			return Collections.singleton(this.getInitialExecutionMode());

		// otherwise, return the internal possible execution modes but with the
		// null value (bottom state) replaced by the initial state.
		return possibleExecutionModes.stream().map(e -> e == null ? this.getInitialExecutionMode() : e)
				.collect(Collectors.toSet());
	}

	public Set<StreamOrdering> getPossibleOrderings() {
		// if no other possible orderings exist.
		if (possibleOrderings.isEmpty())
			// default to the initial ordering.
			return Collections.singleton(this.getInitialOrdering());

		// otherwise, return the internal possible orderings but with the null
		// value (bottom state) replaced by the initial state.
		return possibleOrderings.stream().map(e -> e == null ? this.getInitialOrdering() : e)
				.collect(Collectors.toSet());
	}

	Optional<SSAInvokeInstruction> getInstructionForCreation()
			throws InvalidClassFileException, IOException, CoreException {
		IBytecodeMethod method = (IBytecodeMethod) this.getEnclosingMethodIR().getMethod();
		SimpleName methodName = this.getCreation().getName();

		for (Iterator<SSAInstruction> it = this.getEnclosingMethodIR().iterateNormalInstructions(); it.hasNext();) {
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
								.equals(this.getCreation().getExpression().resolveTypeBinding().getBinaryName())) {
							MethodReference callSiteDeclaredTarget = invokeInstruction.getCallSite()
									.getDeclaredTarget();
							// FIXME: This matching needs much work.
							if (callSiteDeclaredTarget.getName().toString()
									.equals(this.getCreation().resolveMethodBinding().getName())) {
								return Optional.of(invokeInstruction);
							}
						}
					} else
						logger.warning("Instruction: " + instruction + " is not an SSAInstruction.");
				} else
					logger.warning("Instruction: " + instruction + " has no definitions.");
			}
		}
		return Optional.empty();
	}

	private JDTIdentityMapper getJDTIdentifyMapperForCreation() {
		return getJDTIdentifyMapper(this.getCreation());
	}

	public RefactoringStatus getStatus() {
		return status;
	}

	TypeReference getTypeReference() {
		JDTIdentityMapper mapper = getJDTIdentifyMapperForCreation();
		ITypeBinding typeBinding = this.getCreation().resolveTypeBinding();

		// try to get the top-most type.
		ITypeBinding[] allSuperTypes = Bindings.getAllSuperTypes(typeBinding);

		for (ITypeBinding supertype : allSuperTypes) {
			// if it's the top-most interface.
			if (supertype.isInterface() && supertype.getName().startsWith("BaseStream")) {
				typeBinding = supertype; // use it.
				break;
			}
		}

		TypeReference typeRef = mapper.getTypeRef(typeBinding);
		return typeRef;
	}

	private int getUseValueNumberForCreation() throws InvalidClassFileException, IOException, CoreException {
		return getInstructionForCreation().map(i -> i.getUse(0)).orElse(-1);
	}

	private void inferInitialExecution() {
		String methodIdentifier = getMethodIdentifier(this.getCreation().resolveMethodBinding());

		if (methodIdentifier.equals("parallelStream()"))
			this.setInitialExecutionMode(StreamExecutionMode.PARALLEL);
		else
			this.setInitialExecutionMode(StreamExecutionMode.SEQUENTIAL);
	}

	private void inferInitialOrdering()
			throws IOException, CoreException, ClassHierarchyException, InvalidClassFileException,
			InconsistentPossibleStreamSourceOrderingException, NoniterablePossibleStreamSourceException,
			NoninstantiablePossibleStreamSourceException, CannotExtractSpliteratorException {
		ITypeBinding expressionTypeBinding = this.getCreation().getExpression().resolveTypeBinding();
		String expressionTypeQualifiedName = expressionTypeBinding.getErasure().getQualifiedName();
		IMethodBinding calledMethodBinding = this.getCreation().resolveMethodBinding();

		if (JdtFlags.isStatic(calledMethodBinding)) {
			// static methods returning unordered streams.
			if (expressionTypeQualifiedName.equals("java.util.stream.Stream")) {
				String methodIdentifier = getMethodIdentifier(calledMethodBinding);
				if (methodIdentifier.equals("generate(java.util.function.Supplier)"))
					this.setInitialOrdering(StreamOrdering.UNORDERED);
			} else
				this.setInitialOrdering(StreamOrdering.ORDERED);
		} else { // instance method.
			// FIXME: This needs to become interprocedural #7.
			int valueNumber = getUseValueNumberForCreation();
			TypeInference inference = TypeInference.make(this.getEnclosingMethodIR(), false);
			Set<TypeAbstraction> possibleTypes = getPossibleTypes(valueNumber, inference);

			// Possible types: check each one.
			IMethod calledMethod = (IMethod) calledMethodBinding.getJavaElement();
			StreamOrdering ordering = inferInitialStreamOrdering(possibleTypes, calledMethod);
			this.setInitialOrdering(ordering);
		}
	}

	protected void addPossibleExecutionMode(StreamExecutionMode executionMode) {
		this.possibleExecutionModes.add(executionMode);
	}

	protected void addPossibleExecutionModeCollection(
			Collection<? extends StreamExecutionMode> executionModeCollection) {
		this.possibleExecutionModes.addAll(executionModeCollection);
	}

	protected void addPossibleOrderingCollection(Collection<? extends StreamOrdering> orderingCollection) {
		this.possibleOrderings.addAll(orderingCollection);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Stream [streamCreation=");
		builder.append(this.getCreation());
		builder.append(", enclosingMethodDeclaration=");
		builder.append(this.getEnclosingMethodDeclaration());
		builder.append(", possibleExecutionModes=");
		builder.append(this.getPossibleExecutionModes());
		builder.append(", possibleOrderings=");
		builder.append(this.getPossibleOrderings());
		builder.append(", status=");
		builder.append(this.getStatus());
		builder.append("]");
		return builder.toString();
	}

	// TODO: Cache this with a table?
	public InstanceKey getInstanceKey(Collection<InstanceKey> trackedInstances, CallGraph callGraph)
			throws InvalidClassFileException, IOException, CoreException {
		return this.getInstructionForCreation()
				.flatMap(instruction -> trackedInstances.stream()
						.filter(ik -> instanceKeyCorrespondsWithInstantiationInstruction(ik, instruction, callGraph))
						.findFirst())
				.orElseThrow(() -> new IllegalArgumentException("Can't find instance key for stream: " + this
						+ " using tracked instances: " + trackedInstances));
	}

	protected StreamExecutionMode getInitialExecutionMode() {
		return initialExecutionMode;
	}

	protected void setInitialExecutionMode(StreamExecutionMode initialExecutionMode) {
		this.initialExecutionMode = initialExecutionMode;
	}

	protected StreamOrdering getInitialOrdering() {
		return initialOrdering;
	}

	protected void setInitialOrdering(StreamOrdering initialOrdering) {
		this.initialOrdering = initialOrdering;
	}
}
