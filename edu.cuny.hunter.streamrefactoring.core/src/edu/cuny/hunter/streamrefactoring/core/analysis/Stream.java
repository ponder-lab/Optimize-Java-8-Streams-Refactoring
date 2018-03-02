package edu.cuny.hunter.streamrefactoring.core.analysis;

import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.allEqual;
import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.getJDTIdentifyMapper;
import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.getLineNumberFromAST;
import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.getLineNumberFromIR;
import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.getPossibleTypesInterprocedurally;
import static edu.cuny.hunter.streamrefactoring.core.analysis.Util.matches;
import static edu.cuny.hunter.streamrefactoring.core.safe.Util.instanceKeyCorrespondsWithInstantiationInstruction;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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

import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.cast.java.translator.jdt.JDTIdentityMapper;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;

import edu.cuny.hunter.streamrefactoring.core.safe.NoApplicationCodeExistsInCallStringsException;
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

	private static final String BASE_STREAM_TYPE_NAME = "BaseStream";

	private static final String GENERATE_METHOD_ID = "generate(java.util.function.Supplier)";

	private static final String JAVA_UTIL_STREAM_DOUBLE_STREAM = "java.util.stream.DoubleStream";

	private static final String JAVA_UTIL_STREAM_INT_STREAM = "java.util.stream.IntStream";

	private static final String JAVA_UTIL_STREAM_LONG_STREAM = "java.util.stream.LongStream";

	private static final String JAVA_UTIL_STREAM_STREAM = "java.util.stream.Stream";

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	private static final String PARALLEL_STREAM_CREATION_METHOD_ID = "parallelStream()";

	private static final String PLUGIN_ID = FrameworkUtil.getBundle(Stream.class).getSymbolicName();

	private Set<TransformationAction> actions;

	private final MethodInvocation creation;

	private final MethodDeclaration enclosingMethodDeclaration;

	private IR enclosingMethodDeclarationIR;

	private final TypeDeclaration enclosingTypeDeclaration;
	
	private CollectorKind collectorKind;

	private boolean hasNoTerminalOperation;

	private boolean hasPossibleSideEffects;

	private boolean hasPossibleStatefulIntermediateOperations;

	/**
	 * The execution mode derived from the declaration of the stream.
	 */
	private ExecutionMode initialExecutionMode;

	/**
	 * The ordering derived from the declaration of the stream.
	 */
	private Ordering initialOrdering;

	private InstanceKey instanceKey;

	private Optional<SSAInvokeInstruction> instructionForCreation;

	private PreconditionSuccess passingPrecondition;

	/**
	 * This should be the possible execution modes when the stream is consumed by a
	 * terminal operation. Does not include the initial mode.
	 */
	private Set<ExecutionMode> possibleExecutionModes = new HashSet<>();

	/**
	 * This should be the ordering of the stream when it is consumed by a terminal
	 * operation.
	 */
	private Set<Ordering> possibleOrderings = new HashSet<>();

	private boolean reduceOrderingPossiblyMatters;

	/**
	 * The refactoring that this Steam qualifies for. There should be only one as
	 * the refactorings are mutually exclusive.
	 */
	private Refactoring refactoring;

	private RefactoringStatus status = new RefactoringStatus();

	public Stream(MethodInvocation streamCreation) throws ClassHierarchyException, IOException, CoreException,
			InvalidClassFileException, CallGraphBuilderCancelException, CancelException {
		this.creation = streamCreation;
		this.enclosingTypeDeclaration = (TypeDeclaration) ASTNodes.getParent(this.getCreation(),
				ASTNode.TYPE_DECLARATION);
		this.enclosingMethodDeclaration = (MethodDeclaration) ASTNodes.getParent(this.getCreation(),
				ASTNode.METHOD_DECLARATION);

		// Work around #97.
		if (this.enclosingMethodDeclaration == null) {
			LOGGER.warning("Stream: " + this.creation + " not handled.");
			this.addStatusEntry(PreconditionFailure.CURRENTLY_NOT_HANDLED, "Stream: " + this.creation
					+ " is most likely used in a context that is currently not handled by this plug-in.");
		}
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

	void addStatusEntry(PreconditionFailure failure, String message) {
		MethodInvocation creation = this.getCreation();
		CompilationUnit compilationUnit = (CompilationUnit) ASTNodes.getParent(creation, ASTNode.COMPILATION_UNIT);
		ICompilationUnit compilationUnit2 = (ICompilationUnit) compilationUnit.getJavaElement();
		RefactoringStatusContext context = JavaStatusContext.create(compilationUnit2, creation);
		this.getStatus().addEntry(RefactoringStatus.ERROR, message, context, PLUGIN_ID, failure.getCode(), this);
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
		boolean hasNoTerminalOperation = this.hasNoTerminalOperation();

		LOGGER.fine("Execution modes: " + possibleExecutionModes);
		LOGGER.fine("Orderings: " + possibleOrderings);
		LOGGER.fine("Side-effects: " + hasPossibleSideEffects);
		LOGGER.fine("Stateful intermediate operations: " + hasPossibleStatefulIntermediateOperations);
		LOGGER.fine("Reduce ordering matters: " + reduceOrderingPossiblyMatters);
		LOGGER.fine("Terminal operation: " + hasNoTerminalOperation);

		// basically implement the tables.
		MethodInvocation creation = this.getCreation();

		if (hasNoTerminalOperation) {
			// can't do much without a terminal operation.
			LOGGER.warning(() -> "Require terminal operations: " + creation);
			this.addStatusEntry(PreconditionFailure.NO_TERMINAL_OPERATIONS,
					"Require terminal operations: " + creation + ".");
			return;
		}

		// let's check that execution modes are consistent.
		if (this.isConsistent(possibleExecutionModes, PreconditionFailure.INCONSISTENT_POSSIBLE_EXECUTION_MODES,
				"Stream: " + creation + " has inconsitent possible execution modes.", creation))
			// do we have consistent ordering?
			if (this.isConsistent(possibleOrderings, PreconditionFailure.INCONSISTENT_POSSIBLE_ORDERINGS,
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
							this.addStatusEntry(PreconditionFailure.HAS_SIDE_EFFECTS, "Stream: " + creation
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
							this.addStatusEntry(PreconditionFailure.HAS_SIDE_EFFECTS2, "Stream: " + creation
									+ " is associated with a behavioral parameter containing possible side-effects");
						else // check SIO.
						if (!hasPossibleStatefulIntermediateOperations) {
							// it passed P2.
							this.setRefactoring(Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL);
							this.setTransformationAction(TransformationAction.CONVERT_TO_PARALLEL);
							this.setPassingPrecondition(PreconditionSuccess.P2);
						} else // check ROM.
						if (!reduceOrderingPossiblyMatters) {
							// it passes P3.
							this.setRefactoring(Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL);
							this.setTransformationAction(TransformationAction.UNORDER,
									TransformationAction.CONVERT_TO_PARALLEL);
							this.setPassingPrecondition(PreconditionSuccess.P3);
						} else
							this.addStatusEntry(PreconditionFailure.REDUCE_ORDERING_MATTERS,
									"Ordering of the result produced by a terminal operation must be preserved");
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
							this.addStatusEntry(PreconditionFailure.NO_STATEFUL_INTERMEDIATE_OPERATIONS,
									"No stateful intermediate operation exists within the stream's pipeline.");

						break;
					case UNORDERED:
						this.addStatusEntry(PreconditionFailure.UNORDERED, "Stream is unordered.");
						break;
					}
				}
			}
	}

	protected InstanceKey computeInstanceKey(Collection<InstanceKey> trackedInstances,
			EclipseProjectAnalysisEngine<InstanceKey> engine)
			throws InvalidClassFileException, IOException, CoreException, UnhandledCaseException,
			NoApplicationCodeExistsInCallStringsException, InstanceKeyNotFoundException {
		Optional<SSAInvokeInstruction> instructionForCreation = this.getInstructionForCreation(engine);

		if (instructionForCreation.isPresent()) {
			SSAInvokeInstruction instruction = instructionForCreation.get();

			for (InstanceKey ik : trackedInstances)
				if (instanceKeyCorrespondsWithInstantiationInstruction(ik, instruction,
						this.getEnclosingMethodReference(), engine))
					return ik;
		}

		throw new InstanceKeyNotFoundException(
				"Can't find instance key for: " + this.getCreation() + " using tracked instances: " + trackedInstances);
	}

	public Set<TransformationAction> getActions() {
		if (this.actions != null)
			return Collections.unmodifiableSet(this.actions);
		else
			return null;
	}

	public MethodInvocation getCreation() {
		return this.creation;
	}

	public IJavaProject getCreationJavaProject() {
		return this.getEnclosingEclipseMethod().getJavaProject();
	}

	public CompilationUnit getEnclosingCompilationUnit() {
		return (CompilationUnit) ASTNodes.getParent(this.getEnclosingTypeDeclaration(), ASTNode.COMPILATION_UNIT);
	}

	public IMethod getEnclosingEclipseMethod() {
		MethodDeclaration enclosingMethodDeclaration = this.getEnclosingMethodDeclaration();

		if (enclosingMethodDeclaration == null)
			return null;

		IMethodBinding binding = enclosingMethodDeclaration.resolveBinding();
		return (IMethod) binding.getJavaElement();
	}

	public MethodDeclaration getEnclosingMethodDeclaration() {
		return this.enclosingMethodDeclaration;
	}

	private IR getEnclosingMethodIR(EclipseProjectAnalysisEngine<InstanceKey> engine)
			throws IOException, CoreException, UnhandledCaseException {
		if (this.enclosingMethodDeclarationIR == null) {
			// get the IR for the enclosing method.
			com.ibm.wala.classLoader.IMethod resolvedMethod = this.getEnclosingWalaMethod(engine);

			if (resolvedMethod == null)
				throw new UnhandledCaseException("Couldn't retrieve enclosing WALA method. Most likely an AIC #155.");

			this.enclosingMethodDeclarationIR = engine.getCache().getIR(resolvedMethod);

			if (this.enclosingMethodDeclarationIR == null)
				throw new IllegalStateException("IR is null for: " + resolvedMethod);

			LOGGER.fine("IR is: " + this.enclosingMethodDeclarationIR);
		}
		return this.enclosingMethodDeclarationIR;
	}

	/**
	 * @return The {@link CGNode} representing the enclosing method of this stream.
	 * @throws NoEnclosingMethodNodeFoundException
	 *             If the call graph doesn't contain a node for the enclosing
	 *             method.
	 */
	protected CGNode getEnclosingMethodNode(EclipseProjectAnalysisEngine<InstanceKey> engine)
			throws IOException, CoreException, NoEnclosingMethodNodeFoundException {
		MethodReference methodReference = this.getEnclosingMethodReference();
		Set<CGNode> nodes = engine.getCallGraph().getNodes(methodReference);

		if (nodes.isEmpty())
			throw new NoEnclosingMethodNodeFoundException(methodReference);
		else
			return nodes.iterator().next(); // just return the first.
	}

	MethodReference getEnclosingMethodReference() {
		MethodDeclaration enclosingMethodDeclaration = this.getEnclosingMethodDeclaration();
		JDTIdentityMapper mapper = getJDTIdentifyMapper(enclosingMethodDeclaration);
		MethodReference methodRef = mapper.getMethodRef(enclosingMethodDeclaration.resolveBinding());

		if (methodRef == null)
			throw new IllegalStateException(
					"Could not get method reference for: " + enclosingMethodDeclaration.getName());
		return methodRef;
	}

	public IType getEnclosingType() {
		MethodDeclaration enclosingMethodDeclaration = this.getEnclosingMethodDeclaration();

		if (enclosingMethodDeclaration == null)
			return null;

		IMethodBinding binding = enclosingMethodDeclaration.resolveBinding();
		return (IType) binding.getDeclaringClass().getJavaElement();
	}

	public TypeDeclaration getEnclosingTypeDeclaration() {
		return this.enclosingTypeDeclaration;
	}

	public TypeReference getEnclosingTypeReference() {
		JDTIdentityMapper mapper = getJDTIdentifyMapper(this.getEnclosingTypeDeclaration());
		TypeReference ref = mapper.getTypeRef(this.getEnclosingTypeDeclaration().resolveBinding());

		if (ref == null)
			throw new IllegalStateException(
					"Could not get type reference for: " + this.getEnclosingTypeDeclaration().getName());
		return ref;
	}

	public com.ibm.wala.classLoader.IMethod getEnclosingWalaMethod(EclipseProjectAnalysisEngine<InstanceKey> engine)
			throws IOException, CoreException {
		IClassHierarchy classHierarchy = engine.getClassHierarchy();
		MethodReference methodRef = this.getEnclosingMethodReference();
		com.ibm.wala.classLoader.IMethod resolvedMethod = classHierarchy.resolveMethod(methodRef);
		return resolvedMethod;
	}

	protected ExecutionMode getInitialExecutionMode() {
		return this.initialExecutionMode;
	}

	protected Ordering getInitialOrdering() {
		return this.initialOrdering;
	}

	public InstanceKey getInstanceKey(Collection<InstanceKey> trackedInstances,
			EclipseProjectAnalysisEngine<InstanceKey> engine)
			throws InvalidClassFileException, IOException, CoreException, InstanceKeyNotFoundException,
			UnhandledCaseException, NoApplicationCodeExistsInCallStringsException {
		// if not present.
		if (this.instanceKey == null)
			// compute it.
			this.instanceKey = this.computeInstanceKey(trackedInstances, engine);
		return this.instanceKey;
	}

	Optional<SSAInvokeInstruction> getInstructionForCreation(EclipseProjectAnalysisEngine<InstanceKey> engine)
			throws InvalidClassFileException, IOException, CoreException, UnhandledCaseException {
		if (this.instructionForCreation == null) {
			IR enclosingMethodIR = this.getEnclosingMethodIR(engine);

			IBytecodeMethod method = (IBytecodeMethod) enclosingMethodIR.getMethod();
			SimpleName methodName = this.getCreation().getName();

			for (Iterator<SSAInstruction> it = enclosingMethodIR.iterateNormalInstructions(); it.hasNext();) {
				SSAInstruction instruction = it.next();

				int lineNumberFromIR = getLineNumberFromIR(method, instruction);
				int lineNumberFromAST = getLineNumberFromAST(methodName);

				if (lineNumberFromIR == lineNumberFromAST)
					// lines from the AST and the IR match. Let's dive a little
					// deeper to be more confident of the correspondence.
					if (matches(instruction, this.getCreation(), Optional.of(LOGGER))) {
						this.instructionForCreation = Optional.of((SSAInvokeInstruction) instruction);
						return this.instructionForCreation;
					}
			}
			this.instructionForCreation = Optional.empty();
		}
		return this.instructionForCreation;
	}

	private JDTIdentityMapper getJDTIdentifyMapperForCreation() {
		return getJDTIdentifyMapper(this.getCreation());
	}

	public PreconditionSuccess getPassingPrecondition() {
		return this.passingPrecondition;
	}

	public Set<ExecutionMode> getPossibleExecutionModes() {
		// if no other possible execution modes exist.
		ExecutionMode initialExecutionMode = this.getInitialExecutionMode();

		if (this.possibleExecutionModes.isEmpty())
			if (initialExecutionMode == null)
				return null;
			else
				// default to the initial execution mode.
				return Collections.singleton(initialExecutionMode);

		// otherwise, return the internal possible execution modes but with the
		// null value (bottom state) replaced by the initial state.
		return this.possibleExecutionModes.stream().map(e -> e == null ? initialExecutionMode : e)
				.collect(Collectors.toSet());
	}

	public Set<Ordering> getPossibleOrderings() {
		Ordering initialOrdering = this.getInitialOrdering();

		// if no other possible orderings exist.
		if (this.possibleOrderings.isEmpty())
			// default to the initial ordering or null if there isn't any.
			if (initialOrdering == null)
				return null;
			else
				return Collections.singleton(initialOrdering);

		// otherwise, return the internal possible orderings but with the null
		// value (bottom state) replaced by the initial state.
		return this.possibleOrderings.stream().map(e -> e == null ? initialOrdering : e).collect(Collectors.toSet());
	}

	public CollectorKind getCollectorKind() {
		return this.collectorKind;
	}
	
	public Refactoring getRefactoring() {
		return this.refactoring;
	}

	public RefactoringStatus getStatus() {
		return this.status;
	}

	public TypeReference getTypeReference() {
		JDTIdentityMapper mapper = this.getJDTIdentifyMapperForCreation();
		ITypeBinding typeBinding = this.getCreation().resolveTypeBinding();

		// try to get the top-most type.
		ITypeBinding[] allSuperTypes = Bindings.getAllSuperTypes(typeBinding);

		for (ITypeBinding supertype : allSuperTypes)
			// if it's the top-most interface.
			if (supertype.isInterface() && supertype.getName().startsWith(BASE_STREAM_TYPE_NAME)) {
				typeBinding = supertype; // use it.
				break;
			}

		TypeReference typeRef = mapper.getTypeRef(typeBinding);
		return typeRef;
	}

	private int getUseValueNumberForCreation(EclipseProjectAnalysisEngine<InstanceKey> engine)
			throws InvalidClassFileException, IOException, CoreException, UnhandledCaseException {
		return this.getInstructionForCreation(engine).map(i -> i.getUse(0)).orElse(-1);
	}

	public boolean hasNoTerminalOperation() {
		return this.hasNoTerminalOperation;
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
		return this.hasPossibleSideEffects;
	}

	public boolean hasPossibleStatefulIntermediateOperations() {
		return this.hasPossibleStatefulIntermediateOperations;
	}

	public void inferInitialAttributes(EclipseProjectAnalysisEngine<InstanceKey> engine,
			OrderingInference orderingInference) throws InvalidClassFileException, IOException, CoreException,
			UnhandledCaseException, StreamCreationNotConsideredException {
		this.inferInitialExecution();
		this.inferInitialOrdering(engine, orderingInference);
	}

	private void inferInitialExecution() throws JavaModelException {
		String methodIdentifier = Util
				.getMethodIdentifier((IMethod) this.getCreation().resolveMethodBinding().getJavaElement());

		if (methodIdentifier.equals(PARALLEL_STREAM_CREATION_METHOD_ID))
			this.setInitialExecutionMode(ExecutionMode.PARALLEL);
		else
			this.setInitialExecutionMode(ExecutionMode.SEQUENTIAL);
	}

	private void inferInitialOrdering(EclipseProjectAnalysisEngine<InstanceKey> engine,
			OrderingInference orderingInference) throws InvalidClassFileException, IOException, CoreException,
			UnhandledCaseException, StreamCreationNotConsideredException {
		if (this.getCreation().getExpression() == null)
			if (this.getCreation().toString().startsWith("concat("))
				throw new UnhandledCaseException("concat() is not yet implemented.");
			else
				// we don't consider it a new stream.
				throw new StreamCreationNotConsideredException(
						"Creation: " + this.getCreation() + " is not considered to create a new stream.");

		ITypeBinding expressionTypeBinding = this.getCreation().getExpression().resolveTypeBinding();
		String expressionTypeQualifiedName = expressionTypeBinding.getErasure().getQualifiedName();
		IMethodBinding calledMethodBinding = this.getCreation().resolveMethodBinding();

		if (JdtFlags.isStatic(calledMethodBinding))
			// static methods returning unordered streams.
			switch (expressionTypeQualifiedName) {
			case JAVA_UTIL_STREAM_STREAM:
			case JAVA_UTIL_STREAM_INT_STREAM:
			case JAVA_UTIL_STREAM_LONG_STREAM:
			case JAVA_UTIL_STREAM_DOUBLE_STREAM:
				String methodIdentifier = Util.getMethodIdentifier((IMethod) calledMethodBinding.getJavaElement());
				if (methodIdentifier.equals(GENERATE_METHOD_ID))
					this.setInitialOrdering(Ordering.UNORDERED);
				else
					this.setInitialOrdering(Ordering.ORDERED);
				break;
			default:
				// Fall back for now #136.
				Ordering defaultOrdering = Ordering.ORDERED;
				LOGGER.warning(() -> "Unhandled expression type qualified name: " + expressionTypeQualifiedName
						+ ". Falling back to: " + defaultOrdering + ".");
				this.setInitialOrdering(defaultOrdering);
			}
		else { // instance method.
				// get the use value number for the stream creation.
			int valueNumber = this.getUseValueNumberForCreation(engine);

			if (valueNumber < 0) {
				LOGGER.warning("Use value number: " + valueNumber + " for stream creation: "
						+ this.getCreation().getName() + " is invalid. Most likely #155.");
				throw new UnhandledCaseException("Encountered unhandled case, most likely an embedded stream.");
			}

			// get the enclosing method node.
			CGNode node = null;
			try {
				node = this.getEnclosingMethodNode(engine);
			} catch (NoEnclosingMethodNodeFoundException e) {
				LOGGER.log(Level.WARNING, "Can't find enclosing method node for " + this.getCreation()
						+ ". Falling back to: " + Ordering.ORDERED + ".", e);
				this.setInitialOrdering(Ordering.ORDERED);
				return;
			}

			// possible types of the stream creation.
			Collection<TypeAbstraction> possibleTypes = null;
			IMethod calledMethod = null;
			Ordering ordering = null;
			try {
				possibleTypes = getPossibleTypesInterprocedurally(node, valueNumber, engine, orderingInference);

				// Possible types: check each one.
				calledMethod = (IMethod) calledMethodBinding.getJavaElement();
				ordering = orderingInference.inferOrdering(possibleTypes, calledMethod);
			} catch (NoniterableException e) {
				LOGGER.log(Level.WARNING, "Stream: " + this.getCreation()
						+ " has a non-iterable possible source. Falling back to: " + Ordering.ORDERED + ".", e);
				ordering = Ordering.ORDERED;
			} catch (NoninstantiableException e) {
				LOGGER.log(Level.WARNING,
						"Stream: " + this.getCreation() + " has a non-instantiable possible source with type: "
								+ e.getSourceType() + ". Falling back to: " + Ordering.ORDERED + ".",
						e);
				ordering = Ordering.ORDERED;
			} catch (CannotExtractSpliteratorException e) {
				LOGGER.log(Level.WARNING, "Cannot extract spliterator from type: " + e.getFromType() + " for stream: "
						+ this.getCreation() + ". Falling back to: " + Ordering.ORDERED + ".", e);
				ordering = Ordering.ORDERED;
			} catch (InconsistentPossibleOrderingException e) {
				LOGGER.log(Level.WARNING, "Stream: " + this.getCreation()
						+ " has inconsistent possible source orderings. Falling back to: " + Ordering.ORDERED + ".", e);
				ordering = Ordering.ORDERED;
			}

			if (ordering == null) {
				ordering = Ordering.ORDERED;
				LOGGER.warning("Can't find ordering for: " + possibleTypes + " using: " + calledMethod
						+ ". Falling back to: " + ordering);
			}

			this.setInitialOrdering(ordering);
		}
	}

	private boolean isConsistent(Collection<?> collection, PreconditionFailure failure, String failureMessage,
			MethodInvocation streamCreation) {
		if (!allEqual(collection)) {
			this.addStatusEntry(failure, failureMessage);
			return false;
		} else
			return true;
	}

	public boolean reduceOrderingPossiblyMatters() {
		return this.reduceOrderingPossiblyMatters;
	}

	public void setHasNoTerminalOperation(boolean hasNoTerminalOperation) {
		this.hasNoTerminalOperation = hasNoTerminalOperation;
	}

	protected void setHasPossibleSideEffects(boolean hasPossibleSideEffects) {
		this.hasPossibleSideEffects = hasPossibleSideEffects;
	}

	protected void setHasPossibleStatefulIntermediateOperations(boolean hasPossibleStatefulIntermediateOperations) {
		this.hasPossibleStatefulIntermediateOperations = hasPossibleStatefulIntermediateOperations;
	}

	protected void setInitialExecutionMode(ExecutionMode initialExecutionMode) {
		Objects.requireNonNull(initialExecutionMode);
		this.initialExecutionMode = initialExecutionMode;
	}

	protected void setInitialOrdering(Ordering initialOrdering) {
		Objects.requireNonNull(initialOrdering);
		this.initialOrdering = initialOrdering;
	}
	
	protected void setCollectorKind(CollectorKind collectorKind) {
		this.collectorKind = collectorKind;
	}

	private void setPassingPrecondition(PreconditionSuccess succcess) {
		if (this.passingPrecondition == null)
			this.passingPrecondition = succcess;
		else
			throw new IllegalStateException("Passing precondition being set twice.");

	}

	protected void setReduceOrderingPossiblyMatters(boolean reduceOrderingPossiblyMatters) {
		this.reduceOrderingPossiblyMatters = reduceOrderingPossiblyMatters;
	}

	protected void setRefactoring(Refactoring refactoring) {
		if (this.refactoring == null)
			this.refactoring = refactoring;
		else
			throw new IllegalStateException("Refactoring being set twice.");
	}

	protected void setTransformationAction(TransformationAction... actions) {
		if (this.actions == null)
			this.actions = new HashSet<>(Arrays.asList(actions));
		else
			throw new IllegalStateException("Tranformation being set twice.");
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Stream [actions=");
		builder.append(this.actions);
		builder.append(", creation=");
		builder.append(this.creation);
		builder.append(", enclosingMethodDeclaration=");
		builder.append(this.enclosingMethodDeclaration.getName());
		builder.append(", hasPossibleSideEffects=");
		builder.append(this.hasPossibleSideEffects);
		builder.append(", hasPossibleStatefulIntermediateOperations=");
		builder.append(this.hasPossibleStatefulIntermediateOperations);
		builder.append(", hasNoTerminalOperation=");
		builder.append(this.hasNoTerminalOperation);
		builder.append(", initialExecutionMode=");
		builder.append(this.initialExecutionMode);
		builder.append(", initialOrdering=");
		builder.append(this.initialOrdering);
		builder.append(", passingPrecondition=");
		builder.append(this.passingPrecondition);
		builder.append(", possibleExecutionModes=");
		builder.append(this.possibleExecutionModes);
		builder.append(", possibleOrderings=");
		builder.append(this.possibleOrderings);
		builder.append(", reduceOrderingPossiblyMatters=");
		builder.append(this.reduceOrderingPossiblyMatters);
		builder.append(", refactoring=");
		builder.append(this.refactoring);
		builder.append(", status=");
		builder.append(this.status.getSeverity());
		builder.append("]");
		return builder.toString();
	}
}
