package edu.cuny.hunter.streamrefactoring.core.analysis;

import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.allEqual;
import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.getJDTIdentifyMapper;
import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.getPossibleTypesInterprocedurally;
import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.matches;
import static edu.cuny.hunter.streamrefactoring.core.safe.Util.instanceKeyCorrespondsWithInstantiationInstruction;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.osgi.framework.FrameworkUtil;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.cast.java.translator.jdt.JDTIdentityMapper;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.FakeRootMethod;
import com.ibm.wala.ipa.callgraph.impl.FakeWorldClinitMethod;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;

import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;
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

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	private static Map<MethodDeclaration, IR> methodDeclarationToIRMap = new HashMap<>();

	private static final String PLUGIN_ID = FrameworkUtil.getBundle(Stream.class).getSymbolicName();

	public static void clearCaches() {
		javaProjectToClassHierarchyMap.clear();
		javaProjectToAnalysisEngineMap.clear();
		methodDeclarationToIRMap.clear();
		StreamStateMachine.clearCaches();
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

	private final MethodInvocation creation;

	private final MethodDeclaration enclosingMethodDeclaration;

	private final TypeDeclaration enclosingTypeDeclaration;

	/**
	 * The execution mode derived from the declaration of the stream.
	 */
	private ExecutionMode initialExecutionMode;

	/**
	 * This should be the possible execution modes when the stream is consumed by a
	 * terminal operation. Does not include the initial mode.
	 */
	private Set<ExecutionMode> possibleExecutionModes = new HashSet<>();

	private OrderingInference orderingInference;

	/**
	 * The ordering derived from the declaration of the stream.
	 */
	private Ordering initialOrdering;

	/**
	 * This should be the ordering of the stream when it is consumed by a terimal
	 * operation.
	 */
	private Set<Ordering> possibleOrderings = new HashSet<>();

	private boolean hasPossibleSideEffects;

	private boolean hasPossibleStatefulIntermediateOperations;

	private boolean reduceOrderingPossiblyMatters;

	private RefactoringStatus status = new RefactoringStatus();

	private boolean callGraphBuilt;

	private Refactoring refactoring;

	private Set<TransformationAction> actions;

	private PreconditionSuccess passingPrecondition;

	public Stream(MethodInvocation streamCreation) throws ClassHierarchyException, IOException, CoreException,
			InvalidClassFileException, CallGraphBuilderCancelException, CancelException {
		this.creation = streamCreation;
		this.enclosingTypeDeclaration = (TypeDeclaration) ASTNodes.getParent(this.getCreation(),
				ASTNode.TYPE_DECLARATION);
		this.enclosingMethodDeclaration = (MethodDeclaration) ASTNodes.getParent(this.getCreation(),
				ASTNode.METHOD_DECLARATION);

		// Work around #97.
		if (this.enclosingMethodDeclaration == null) {
			LOGGER.warning("Stream: " + creation + " not handled.");
			this.addStatusEntry(PreconditionFailure.CURRENTLY_NOT_HANDLED, "Stream: " + creation
					+ " is most likely used in a context that is currently not handled by this plug-in.");
			return;
		}

		this.orderingInference = new OrderingInference(this.getClassHierarchy());

		this.inferInitialExecution();

		try {
			this.inferInitialOrdering();
		} catch (InconsistentPossibleOrderingException e) {
			LOGGER.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(PreconditionFailure.INCONSISTENT_POSSIBLE_STREAM_SOURCE_ORDERING,
					"Stream: " + streamCreation + " has inconsistent possible source orderings.");
		} catch (NoniterableException e) {
			LOGGER.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(PreconditionFailure.NON_ITERABLE_POSSIBLE_STREAM_SOURCE,
					"Stream: " + streamCreation + " has a non-iterable possible source.");
		} catch (NoninstantiableException e) {
			LOGGER.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(PreconditionFailure.NON_INSTANTIABLE_POSSIBLE_STREAM_SOURCE, "Stream: " + streamCreation
					+ " has a non-instantiable possible source with type: " + e.getSourceType() + ".");
		} catch (CannotExtractSpliteratorException e) {
			LOGGER.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(PreconditionFailure.NON_DETERMINABLE_STREAM_SOURCE_ORDERING,
					"Cannot extract spliterator from type: " + e.getFromType() + " for stream: " + streamCreation
							+ ".");
		}

		try {
			// start the state machine.
			new StreamStateMachine(this).start();

			// check preconditions.
			this.check();
		} catch (PropertiesException | CancelException | NoniterableException | NoninstantiableException
				| CannotExtractSpliteratorException e) {
			LOGGER.log(Level.SEVERE, "Error while building stream.", e);
			throw new RuntimeException(e);
		} catch (UnknownIfReduceOrderMattersException e) {
			LOGGER.log(Level.WARNING, "Exception caught while processing: " + streamCreation, e);
			addStatusEntry(PreconditionFailure.NON_DETERMINABLE_REDUCTION_ORDERING,
					"Cannot derive reduction ordering for stream: " + streamCreation + ".");
		} catch (RequireTerminalOperationException e) {
			LOGGER.log(Level.WARNING, "Require terminal operations: " + streamCreation, e);
			addStatusEntry(PreconditionFailure.NO_TERMINAL_OPERATIONS,
					"Require terminal operations: " + streamCreation + ".");
		} catch (InstanceKeyNotFoundException | NoEnclosingMethodNodeFoundException e) {
			LOGGER.log(Level.WARNING, "Encountered probable unhandled case while processing: " + streamCreation, e);
			addStatusEntry(PreconditionFailure.CURRENTLY_NOT_HANDLED, "Encountered probably unhandled case.");
		}
	}

	/**
	 * Check this {@link Stream} for precondition failures.
	 */
	protected void check() {
		Set<ExecutionMode> possibleExecutionModes = this.getPossibleExecutionModes();
		Set<Ordering> possibleOrderings = this.getPossibleOrderings();
		boolean hasPossibleSideEffects = this.hasPossibleSideEffects();
		boolean hasPossibleStatefulIntermediateOperations = this.hasPossibleStatefulIntermediateOperations();
		boolean reduceOrderingPossiblyMatters = this.reduceOrderingPossiblyMatters();

		LOGGER.info("Execution modes: " + possibleExecutionModes);
		LOGGER.info("Orderings: " + possibleOrderings);
		LOGGER.info("Side-effects: " + hasPossibleSideEffects);
		LOGGER.info("Stateful intermediate operations: " + hasPossibleStatefulIntermediateOperations);
		LOGGER.info("Reduce ordering matters: " + reduceOrderingPossiblyMatters);

		// basically implement the tables.

		// first, let's check that execution modes are consistent.
		MethodInvocation creation = this.getCreation();

		if (isConsistent(possibleExecutionModes, PreconditionFailure.INCONSISTENT_POSSIBLE_EXECUTION_MODES,
				"Stream: " + creation + " has inconsitent possible execution modes.", creation)) {
			// do we have consistent ordering?
			if (isConsistent(possibleOrderings, PreconditionFailure.INCONSISTENT_POSSIBLE_ORDERINGS,
					"Stream: " + creation + " has inconsitent possible orderings.", creation)) {
				ExecutionMode executionMode = possibleExecutionModes.iterator().next();
				assert executionMode != null : "Execution mode is null";

				Ordering ordering = possibleOrderings.iterator().next();
				assert ordering != null : "Ordering is null";

				switch (executionMode) {
				case SEQUENTIAL:
					// table 1.
					switch (ordering) {
					case UNORDERED:
						if (hasPossibleSideEffects)
							addStatusEntry(PreconditionFailure.HAS_SIDE_EFFECTS, "Stream: " + creation
									+ " is associated with a behavioral parameter containing possible side-effects");
						else {
							// it passed P1.
							this.setRefactoring(Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL);
							this.setTransformationAction(TransformationAction.CONVERT_TO_PARALLEL);
							this.setPassingPrecondition(PreconditionSuccess.P1);
						}
						break;
					case ORDERED:
						if (hasPossibleSideEffects)
							addStatusEntry(PreconditionFailure.HAS_SIDE_EFFECTS2, "Stream: " + creation
									+ " is associated with a behavioral parameter containing possible side-effects");
						else {
							// check SIO.
							if (!hasPossibleStatefulIntermediateOperations) {
								// it passed P2.
								this.setRefactoring(Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL);
								this.setTransformationAction(TransformationAction.CONVERT_TO_PARALLEL);
								this.setPassingPrecondition(PreconditionSuccess.P2);
							} else {
								// check ROM.
								if (!reduceOrderingPossiblyMatters) {
									// it passes P3.
									this.setRefactoring(Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL);
									this.setTransformationAction(TransformationAction.UNORDER,
											TransformationAction.CONVERT_TO_PARALLEL);
									this.setPassingPrecondition(PreconditionSuccess.P3);
								} else
									addStatusEntry(PreconditionFailure.REDUCE_ORDERING_MATTERS,
											"Ordering of the result produced by a terminal operation must be preserved");
							}
						}
						break;
					}
					break;
				case PARALLEL:
					// table 2.
					switch (ordering) {
					case ORDERED:
						if (hasPossibleStatefulIntermediateOperations) {
							if (!reduceOrderingPossiblyMatters) {
								// it passes P4.
								this.setRefactoring(Refactoring.OPTIMIZE_PARALLEL_STREAM);
								this.setTransformationAction(TransformationAction.UNORDER);
								this.setPassingPrecondition(PreconditionSuccess.P4);
							} else {
								// it passes P5.
								this.setRefactoring(Refactoring.OPTIMIZE_PARALLEL_STREAM);
								this.setTransformationAction(TransformationAction.CONVERT_TO_SEQUENTIAL);
								this.setPassingPrecondition(PreconditionSuccess.P5);
							}
						} else
							addStatusEntry(PreconditionFailure.NO_STATEFUL_INTERMEDIATE_OPERATIONS,
									"No stateful intermediate operation exists within the stream's pipeline.");

						break;
					case UNORDERED:
						addStatusEntry(PreconditionFailure.UNORDERED, "Stream is unordered.");
						break;
					}
				}
			}
		}
	}

	private void setPassingPrecondition(PreconditionSuccess succcess) {
		if (this.passingPrecondition == null)
			this.passingPrecondition = succcess;
		else
			throw new IllegalStateException("Passing precondition being set twice.");

	}

	protected void setTransformationAction(TransformationAction... actions) {
		if (this.actions == null)
			this.actions = new HashSet<>(Arrays.asList(actions));
		else
			throw new IllegalStateException("Tranformation being set twice.");
	}

	protected void setRefactoring(Refactoring refactoring) {
		if (this.refactoring == null)
			this.refactoring = refactoring;
		else
			throw new IllegalStateException("Refactoring being set twice.");
	}

	private boolean isConsistent(Collection<?> collection, PreconditionFailure failure, String failureMessage,
			MethodInvocation streamCreation) {
		if (!allEqual(collection)) {
			addStatusEntry(failure, failureMessage);
			return false;
		} else
			return true;
	}

	void addStatusEntry(PreconditionFailure failure, String message) {
		MethodInvocation creation = this.getCreation();
		CompilationUnit compilationUnit = (CompilationUnit) ASTNodes.getParent(creation, ASTNode.COMPILATION_UNIT);
		ICompilationUnit compilationUnit2 = (ICompilationUnit) compilationUnit.getJavaElement();
		RefactoringStatusContext context = JavaStatusContext.create(compilationUnit2, creation);
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
		return this.getEnclosingEclipseMethod().getJavaProject();
	}

	public IMethod getEnclosingEclipseMethod() {
		return (IMethod) getEnclosingMethodDeclaration().resolveBinding().getJavaElement();
	}

	public MethodDeclaration getEnclosingMethodDeclaration() {
		return enclosingMethodDeclaration;
	}

	public CompilationUnit getEnclosingCompilationUnit() {
		return (CompilationUnit) ASTNodes.getParent(this.getEnclosingTypeDeclaration(), ASTNode.COMPILATION_UNIT);
	}

	private IR getEnclosingMethodIR() throws IOException, CoreException {
		IR ir = methodDeclarationToIRMap.get(getEnclosingMethodDeclaration());

		if (ir == null) {
			// get the IR for the enclosing method.
			com.ibm.wala.classLoader.IMethod resolvedMethod = getEnclosingWalaMethod();
			ir = this.getAnalysisEngine().getCache().getIR(resolvedMethod);

			if (ir == null)
				throw new IllegalStateException("IR is null for: " + resolvedMethod);

			LOGGER.info("IR is: " + ir.toString());

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

	public Set<ExecutionMode> getPossibleExecutionModes() {
		// if no other possible execution modes exist.
		ExecutionMode initialExecutionMode = this.getInitialExecutionMode();

		if (possibleExecutionModes.isEmpty())
			if (initialExecutionMode == null)
				return null;
			else
				// default to the initial execution mode.
				return Collections.singleton(initialExecutionMode);

		// otherwise, return the internal possible execution modes but with the
		// null value (bottom state) replaced by the initial state.
		return possibleExecutionModes.stream().map(e -> e == null ? initialExecutionMode : e)
				.collect(Collectors.toSet());
	}

	public Set<Ordering> getPossibleOrderings() {
		Ordering initialOrdering = this.getInitialOrdering();

		// if no other possible orderings exist.
		if (possibleOrderings.isEmpty())
			// default to the initial ordering or null if there isn't any.
			if (initialOrdering == null)
				return null;
			else
				return Collections.singleton(initialOrdering);

		// otherwise, return the internal possible orderings but with the null
		// value (bottom state) replaced by the initial state.
		return possibleOrderings.stream().map(e -> e == null ? initialOrdering : e).collect(Collectors.toSet());
	}

	Optional<SSAInvokeInstruction> getInstructionForCreation()
			throws InvalidClassFileException, IOException, CoreException {
		IBytecodeMethod method = (IBytecodeMethod) this.getEnclosingMethodIR().getMethod();
		SimpleName methodName = this.getCreation().getName();

		for (Iterator<SSAInstruction> it = this.getEnclosingMethodIR().iterateNormalInstructions(); it.hasNext();) {
			SSAInstruction instruction = it.next();

			int lineNumberFromIR = getLineNumberFromIR(method, instruction);
			int lineNumberFromAST = getLineNumberFromAST(methodName);

			if (lineNumberFromIR == lineNumberFromAST) {
				// lines from the AST and the IR match. Let's dive a little
				// deeper to be more confident of the correspondence.
				if (matches(instruction, this.getCreation(), Optional.of(LOGGER)))
					return Optional.of((SSAInvokeInstruction) instruction);
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

	public TypeReference getTypeReference() {
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
			this.setInitialExecutionMode(ExecutionMode.PARALLEL);
		else
			this.setInitialExecutionMode(ExecutionMode.SEQUENTIAL);
	}

	private void inferInitialOrdering()
			throws IOException, CoreException, ClassHierarchyException, InvalidClassFileException,
			InconsistentPossibleOrderingException, NoniterableException, NoninstantiableException,
			CannotExtractSpliteratorException, CallGraphBuilderCancelException, CancelException {
		ITypeBinding expressionTypeBinding = this.getCreation().getExpression().resolveTypeBinding();
		String expressionTypeQualifiedName = expressionTypeBinding.getErasure().getQualifiedName();
		IMethodBinding calledMethodBinding = this.getCreation().resolveMethodBinding();

		// build the graph.
		this.buildCallGraph();

		if (JdtFlags.isStatic(calledMethodBinding)) {
			// static methods returning unordered streams.
			if (expressionTypeQualifiedName.equals("java.util.stream.Stream")
					|| expressionTypeQualifiedName.equals("java.util.stream.IntStream")
					|| expressionTypeQualifiedName.equals("java.util.stream.LongStream")
					|| expressionTypeQualifiedName.equals("java.util.stream.DoubleStream")) {
				String methodIdentifier = getMethodIdentifier(calledMethodBinding);
				if (methodIdentifier.equals("generate(java.util.function.Supplier)"))
					this.setInitialOrdering(Ordering.UNORDERED);
				else
					this.setInitialOrdering(Ordering.ORDERED);
			}
		} else { // instance method.
			int valueNumber = getUseValueNumberForCreation();

			// get the enclosing method node.
			CGNode node = null;
			try {
				node = this.getEnclosingMethodNode();
			} catch (NoEnclosingMethodNodeFoundException e) {
				LOGGER.log(Level.WARNING, "Can't find enclosing method node for " + this.getCreation()
						+ ". Falling back to " + Ordering.ORDERED, e);
				this.setInitialOrdering(Ordering.ORDERED);
				return;
			}

			Collection<TypeAbstraction> possibleTypes = getPossibleTypesInterprocedurally(node, valueNumber,
					this.getAnalysisEngine().getHeapGraph().getHeapModel(),
					this.getAnalysisEngine().getPointerAnalysis(), this, LOGGER);

			// Possible types: check each one.
			IMethod calledMethod = (IMethod) calledMethodBinding.getJavaElement();

			Ordering ordering = this.getOrderingInference().inferOrdering(possibleTypes, calledMethod);

			if (ordering == null) {
				ordering = Ordering.ORDERED;
				LOGGER.warning("Can't find ordering for: " + possibleTypes + " using: " + calledMethod
						+ ". Falling back to: " + ordering);
			}

			this.setInitialOrdering(ordering);
		}
	}

	/**
	 * @return The {@link CGNode} representing the enclosing method of this stream.
	 * @throws NoEnclosingMethodNodeFoundException
	 *             If the call graph doesn't contain a node for the enclosing
	 *             method.
	 */
	protected CGNode getEnclosingMethodNode() throws IOException, CoreException, NoEnclosingMethodNodeFoundException {
		MethodReference methodReference = this.getEnclosingMethodReference();
		Set<CGNode> nodes = this.getAnalysisEngine().getCallGraph().getNodes(methodReference);

		if (nodes.isEmpty())
			throw new NoEnclosingMethodNodeFoundException(methodReference);
		else if (nodes.size() == 1 || nodes.size() > 1 && allFake(nodes, this.getAnalysisEngine().getCallGraph()))
			return nodes.iterator().next(); // just return the first.
		else
			throw new IllegalStateException("Unexpected number of nodes: " + nodes.size());

	}

	/**
	 * Returns true iff all of the predecessors of all of the given {@link CGNode}s
	 * in the {@link CallGraph} are {@link FakeRootMethod}s.
	 * 
	 * @param nodes
	 *            The nodes whose predecessors to consider.
	 * @param callGraph
	 *            The {@link CallGraph} to search.
	 * @return True iff all of the predecessors of all of the given {@link CGNode}s
	 *         in the {@link CallGraph} are {@link FakeRootMethod}s.
	 * @apiNote The may be an issue here related to #106.
	 */
	private static boolean allFake(Set<CGNode> nodes, CallGraph callGraph) {
		// for each node.
		for (CGNode cgNode : nodes) {
			// for each predecessor.
			for (Iterator<CGNode> it = callGraph.getPredNodes(cgNode); it.hasNext();) {
				CGNode predNode = it.next();
				com.ibm.wala.classLoader.IMethod predMethod = predNode.getMethod();

				boolean isFakeMethod = predMethod instanceof FakeRootMethod
						|| predMethod instanceof FakeWorldClinitMethod;

				if (!isFakeMethod)
					return false;
			}
		}
		return true;
	}

	protected void addPossibleExecutionMode(ExecutionMode executionMode) {
		this.possibleExecutionModes.add(executionMode);
	}

	protected void addPossibleExecutionModeCollection(Collection<? extends ExecutionMode> executionModeCollection) {
		this.possibleExecutionModes.addAll(executionModeCollection);
	}

	protected void addPossibleOrderingCollection(Collection<? extends Ordering> orderingCollection) {
		this.possibleOrderings.addAll(orderingCollection);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Stream [streamCreation=");
		builder.append(this.getCreation());
		builder.append(", enclosingMethodDeclaration=");
		builder.append(this.getEnclosingMethodDeclaration().getName());
		builder.append(", possibleExecutionModes=");
		builder.append(this.getPossibleExecutionModes());
		builder.append(", possibleOrderings=");
		builder.append(this.getPossibleOrderings());
		builder.append(", status=");
		builder.append(this.getStatus().getSeverity());
		builder.append("]");
		return builder.toString();
	}

	// TODO: Cache this with a table?
	public InstanceKey getInstanceKey(Collection<InstanceKey> trackedInstances, CallGraph callGraph)
			throws InvalidClassFileException, IOException, CoreException, InstanceKeyNotFoundException {
		return this.getInstructionForCreation()
				.flatMap(instruction -> trackedInstances.stream()
						.filter(ik -> instanceKeyCorrespondsWithInstantiationInstruction(ik, instruction, callGraph))
						.findFirst())
				.orElseThrow(() -> new InstanceKeyNotFoundException("Can't find instance key for: " + this.getCreation()
						+ " using tracked instances: " + trackedInstances));
	}

	protected ExecutionMode getInitialExecutionMode() {
		return initialExecutionMode;
	}

	protected void setInitialExecutionMode(ExecutionMode initialExecutionMode) {
		Objects.requireNonNull(initialExecutionMode);
		this.initialExecutionMode = initialExecutionMode;
	}

	protected Ordering getInitialOrdering() {
		return initialOrdering;
	}

	protected void setInitialOrdering(Ordering initialOrdering) {
		Objects.requireNonNull(initialOrdering);
		this.initialOrdering = initialOrdering;
	}

	/**
	 * Returns true iff any behavioral parameters (λ-expressions) associated with
	 * any operations in the stream's pipeline has side-effects on any possible
	 * path. TODO: What if one path has side-effects and the other doesn't?
	 * 
	 * @return true iff any behavioral parameters (λ-expressions) associated with
	 *         any operations in the stream's pipeline has side-effects on any
	 *         possible path.
	 */
	public boolean hasPossibleSideEffects() {
		return hasPossibleSideEffects;
	}

	protected void setHasPossibleSideEffects(boolean hasPossibleSideEffects) {
		this.hasPossibleSideEffects = hasPossibleSideEffects;
	}

	public boolean hasPossibleStatefulIntermediateOperations() {
		return hasPossibleStatefulIntermediateOperations;
	}

	protected void setHasPossibleStatefulIntermediateOperations(boolean hasPossibleStatefulIntermediateOperations) {
		this.hasPossibleStatefulIntermediateOperations = hasPossibleStatefulIntermediateOperations;
	}

	public boolean reduceOrderingPossiblyMatters() {
		return this.reduceOrderingPossiblyMatters;
	}

	protected void setReduceOrderingPossiblyMatters(boolean reduceOrderingPossiblyMatters) {
		this.reduceOrderingPossiblyMatters = reduceOrderingPossiblyMatters;
	}

	protected OrderingInference getOrderingInference() {
		return orderingInference;
	}

	protected void buildCallGraph()
			throws IOException, CoreException, CallGraphBuilderCancelException, CancelException {
		if (!this.isCallGraphBuilt()) {
			// FIXME: Do we want a different entry point?
			// TODO: Do we need to build the call graph for each stream?
			Set<Entrypoint> entryPoints = Util.findEntryPoints(getAnalysisEngine().getClassHierarchy());

			// set options.
			AnalysisOptions options = getAnalysisEngine().getDefaultOptions(entryPoints);
			// TODO turn off reflection analysis for now.
			options.setReflectionOptions(ReflectionOptions.NONE);
			options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());

			// FIXME: Do we need to build a new call graph for each entry point?
			// Doesn't make sense. Maybe we need to collect all enclosing
			// methods
			// and use those as entry points.
			getAnalysisEngine().buildSafeCallGraph(options);
			// TODO: Can I slice the graph so that only nodes relevant to the
			// instance in question are present?

			this.setCallGraphBuilt(true);
		}
	}

	protected boolean isCallGraphBuilt() {
		return callGraphBuilt;
	}

	protected void setCallGraphBuilt(boolean callGraphBuilt) {
		this.callGraphBuilt = callGraphBuilt;
	}

	public Refactoring getRefactoring() {
		return this.refactoring;
	}

	public Set<TransformationAction> getActions() {
		if (this.actions != null)
			return Collections.unmodifiableSet(this.actions);
		else
			return null;
	}

	public PreconditionSuccess getPassingPrecondition() {
		return this.passingPrecondition;
	}

	static Map<MethodDeclaration, IR> getMethodDeclarationToIRMap() {
		return Collections.unmodifiableMap(methodDeclarationToIRMap);
	}
}
