package edu.cuny.hunter.streamrefactoring.core.analysis;

import static com.ibm.safe.typestate.core.AbstractWholeProgramSolver.DUMMY_ZERO;
import static edu.cuny.hunter.streamrefactoring.core.analysis.StreamAttributeTypestateRule.BOTTOM_STATE_NAME;

import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.JavaModelException;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.ibm.safe.Factoid;
import com.ibm.safe.ICFGSupergraph;
import com.ibm.safe.controller.ISafeSolver;
import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.internal.exceptions.MaxFindingsException;
import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.internal.exceptions.SetUpException;
import com.ibm.safe.internal.exceptions.SolverTimeoutException;
import com.ibm.safe.options.WholeProgramProperties;
import com.ibm.safe.properties.PropertiesManager;
import com.ibm.safe.reporting.message.AggregateSolverResult;
import com.ibm.safe.rules.TypestateRule;
import com.ibm.safe.typestate.base.BaseFactoid;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.core.TypeStateResult;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.wala.analysis.typeInference.JavaPrimitiveType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.client.AnalysisEngine;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceFieldPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.pruned.PrunedCallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.modref.ModRef;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.strings.Atom;

import edu.cuny.hunter.streamrefactoring.core.safe.ModifiedBenignOracle;
import edu.cuny.hunter.streamrefactoring.core.safe.NoApplicationCodeExistsInCallStringsException;
import edu.cuny.hunter.streamrefactoring.core.safe.TypestateSolverFactory;
import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;
import edu.cuny.hunter.streamrefactoring.core.wala.CallStringWithReceivers;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

public class StreamStateMachine {

	public class Statistics {
		private int numberOfStreamInstancesProcessed;
		private int numberOfStreamInstancesSkipped;

		public Statistics(int numberOfStreamInstancesProcessed, int numberOfStreamInstancesSkipped) {
			this.numberOfStreamInstancesProcessed = numberOfStreamInstancesProcessed;
			this.numberOfStreamInstancesSkipped = numberOfStreamInstancesSkipped;
		}

		public int getNumberOfStreamInstancesProcessed() {
			return this.numberOfStreamInstancesProcessed;
		}

		public int getNumberOfStreamInstancesSkipped() {
			return this.numberOfStreamInstancesSkipped;
		}
	}

	@SuppressWarnings("unused")
	private static final String ARRAYS_STREAM_CREATION_METHOD_NAME = "Arrays.stream";

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	/**
	 * A list of stateful intermediate operation signatures.
	 */
	// @formatter:off
	private static final String[] STATEFUL_INTERMEDIATE_OPERATIONS = { "java.util.stream.Stream.distinct",
			"java.util.stream.Stream.limit", "java.util.stream.Stream.skip", "java.util.stream.DoubleStream.distinct",
			"java.util.stream.DoubleStream.limit", "java.util.stream.DoubleStream.skip",
			"java.util.stream.IntStream.distinct", "java.util.stream.IntStream.limit",
			"java.util.stream.IntStream.skip", "java.util.stream.LongStream.distinct",
			"java.util.stream.LongStream.limit", "java.util.stream.LongStream.skip" };
	// @formatter:on

	private static final Atom STREAM_PACKAGE_ATOM = Atom.findOrCreateUnicodeAtom("java/util/stream");

	/**
	 * A list of supported terminal operation signatures.
	 */
	// @formatter:off
	private static final String[] TERMINAL_OPERATIONS = { "java.util.stream.DoubleStream.forEach",
			"java.util.stream.DoubleStream.forEachOrdered", "java.util.stream.DoubleStream.toArray",
			"java.util.stream.DoubleStream.reduce", "java.util.stream.DoubleStream.collect",
			"java.util.stream.DoubleStream.sum", "java.util.stream.DoubleStream.min",
			"java.util.stream.DoubleStream.max", "java.util.stream.DoubleStream.count",
			"java.util.stream.DoubleStream.average", "java.util.stream.DoubleStream.summaryStatistics",
			"java.util.stream.DoubleStream.anyMatch", "java.util.stream.DoubleStream.allMatch",
			"java.util.stream.DoubleStream.noneMatch", "java.util.stream.DoubleStream.findFirst",
			"java.util.stream.DoubleStream.findAny", "java.util.stream.IntStream.forEach",
			"java.util.stream.IntStream.forEachOrdered", "java.util.stream.IntStream.toArray",
			"java.util.stream.IntStream.reduce", "java.util.stream.IntStream.collect", "java.util.stream.IntStream.sum",
			"java.util.stream.IntStream.min", "java.util.stream.IntStream.max", "java.util.stream.IntStream.count",
			"java.util.stream.IntStream.average", "java.util.stream.IntStream.summaryStatistics",
			"java.util.stream.IntStream.anyMatch", "java.util.stream.IntStream.allMatch",
			"java.util.stream.IntStream.noneMatch", "java.util.stream.IntStream.findFirst",
			"java.util.stream.IntStream.findAny", "java.util.stream.LongStream.forEach",
			"java.util.stream.LongStream.forEachOrdered", "java.util.stream.LongStream.toArray",
			"java.util.stream.LongStream.reduce", "java.util.stream.LongStream.collect",
			"java.util.stream.LongStream.sum", "java.util.stream.LongStream.min", "java.util.stream.LongStream.max",
			"java.util.stream.LongStream.count", "java.util.stream.LongStream.average",
			"java.util.stream.LongStream.summaryStatistics", "java.util.stream.LongStream.anyMatch",
			"java.util.stream.LongStream.allMatch", "java.util.stream.LongStream.noneMatch",
			"java.util.stream.LongStream.findFirst", "java.util.stream.LongStream.findAny",
			"java.util.stream.Stream.forEach", "java.util.stream.Stream.forEachOrdered",
			"java.util.stream.Stream.toArray", "java.util.stream.Stream.reduce", "java.util.stream.Stream.collect",
			"java.util.stream.Stream.min", "java.util.stream.Stream.max", "java.util.stream.Stream.count",
			"java.util.stream.Stream.anyMatch", "java.util.stream.Stream.allMatch", "java.util.stream.Stream.noneMatch",
			"java.util.stream.Stream.findFirst", "java.util.stream.Stream.findAny" };
	// @formatter:on

	// @formatter:off
	private static final String[] TERMINAL_OPERATIONS_WERE_REDUCE_ORDERING_MATTERS = {
			"java.util.stream.DoubleStream.forEachOrdered", "java.util.stream.IntStream.forEachOrdered",
			"java.util.stream.LongStream.forEachOrdered", "java.util.stream.Stream.forEachOrdered",
			"java.util.stream.DoubleStream.findFirst", "java.util.stream.IntStream.findFirst",
			"java.util.stream.LongStream.findFirst", "java.util.stream.Stream.findFirst" };
	// @formatter:on

	// @formatter:off
	private static final String[] TERMINAL_OPERATIONS_WHERE_REDUCE_ORDERING_DOES_NOT_MATTER = {
			"java.util.stream.DoubleStream.forEach", "java.util.stream.IntStream.forEach",
			"java.util.stream.LongStream.forEach", "java.util.stream.Stream.forEach",
			"java.util.stream.DoubleStream.sum", "java.util.stream.IntStream.sum", "java.util.stream.LongStream.sum",
			"java.util.stream.IntStream.min", "java.util.stream.DoubleStream.min", "java.util.stream.LongStream.min",
			"java.util.stream.Stream.min", "java.util.stream.DoubleStream.max", "java.util.stream.IntStream.max",
			"java.util.stream.LongStream.max", "java.util.stream.Stream.max", "java.util.stream.DoubleStream.count",
			"java.util.stream.IntStream.count", "java.util.stream.LongStream.count", "java.util.stream.Stream.count",
			"java.util.stream.DoubleStream.average", "java.util.stream.IntStream.average",
			"java.util.stream.LongStream.average", "java.util.stream.DoubleStream.summaryStatistics",
			"java.util.stream.IntStream.summaryStatistics", "java.util.stream.LongStream.summaryStatistics",
			"java.util.stream.DoubleStream.anyMatch", "java.util.stream.IntStream.anyMatch",
			"java.util.stream.LongStream.anyMatch", "java.util.stream.Stream.anyMatch",
			"java.util.stream.DoubleStream.allMatch", "java.util.stream.IntStream.allMatch",
			"java.util.stream.LongStream.allMatch", "java.util.stream.Stream.allMatch",
			"java.util.stream.DoubleStream.noneMatch", "java.util.stream.IntStream.noneMatch",
			"java.util.stream.LongStream.noneMatch", "java.util.stream.Stream.noneMatch",
			"java.util.stream.DoubleStream.findAny", "java.util.stream.IntStream.findAny",
			"java.util.stream.LongStream.findAny", "java.util.stream.Stream.findAny" };
	// @formatter:on

	// @formatter:off
	private static final String[] TERMINAL_OPERATIONS_WHERE_REDUCE_ORDERING_MATTERS_IS_UNKNOWN = {
			"java.util.stream.DoubleStream.reduce", "java.util.stream.IntStream.reduce",
			"java.util.stream.LongStream.reduce", "java.util.stream.Stream.reduce",
			"java.util.stream.DoubleStream.collect", "java.util.stream.IntStream.collect",
			"java.util.stream.LongStream.collect", "java.util.stream.Stream.collect", };
	// @formatter:on

	/**
	 * The typestate rules to use.
	 */
	protected static StreamAttributeTypestateRule[] createStreamAttributeTypestateRules(IClass streamClass) {
		// @formatter:off
		return new StreamAttributeTypestateRule[] { new StreamExecutionModeTypeStateRule(streamClass),
				new StreamOrderingTypeStateRule(streamClass) };
		// @formatter:on
	}

	private static boolean deriveRomForScalarMethod(SSAInvokeInstruction invokeInstruction)
			throws UnknownIfReduceOrderMattersException {
		MethodReference declaredTarget = invokeInstruction.getCallSite().getDeclaredTarget();

		if (isTerminalOperationWhereReduceOrderMattersIsUnknown(declaredTarget))
			throw new UnknownIfReduceOrderMattersException(
					"Cannot decifer whether ordering matters for method: " + declaredTarget);
		else
			// otherwise, it's the same as the void case.
			return deriveRomForVoidMethod(invokeInstruction);
	}

	private static boolean deriveRomForVoidMethod(SSAInvokeInstruction invokeInstruction)
			throws UnknownIfReduceOrderMattersException {
		MethodReference declaredTarget = invokeInstruction.getCallSite().getDeclaredTarget();

		if (isTerminalOperationWhereReduceOrderMatters(declaredTarget))
			return true;
		else if (isTerminalOperationWhereReduceOrderDoesNotMatter(declaredTarget))
			return false;
		else
			throw new UnknownIfReduceOrderMattersException("Can't decipher ROM for method: " + declaredTarget + ".");
	}

	/**
	 * Returns true if the given {@link PointerKey} should be filtered from the
	 * {@link ModRef} analysis.
	 *
	 * @param pointerKey The {@link PointerKey} in question.
	 * @param engine     The {@link AnalysisEngine} to use.
	 * @return <code>true</code> if the given {@link PointerKey} should be filtered
	 *         and <code>false</code> otherwise.
	 * @apiNote The current filtering mechanism excludes field {@link PointerKey}s
	 *          whose instance is being assigned with the stream package. Basically,
	 *          we are looking for modifications to the client code.
	 */
	private static boolean filterPointerKey(PointerKey pointerKey, EclipseProjectAnalysisEngine<InstanceKey> engine) {
		Boolean ret = null;

		if (pointerKey instanceof InstanceFieldPointerKey) {
			InstanceFieldPointerKey fieldPointerKey = (InstanceFieldPointerKey) pointerKey;
			InstanceKey instanceKey = fieldPointerKey.getInstanceKey();

			for (Iterator<Pair<CGNode, NewSiteReference>> creationSiteIterator = instanceKey
					.getCreationSites(engine.getCallGraph()); creationSiteIterator.hasNext();) {
				Pair<CGNode, NewSiteReference> creationSite = creationSiteIterator.next();
				TypeReference declaredType = creationSite.snd.getDeclaredType();
				TypeName name = declaredType.getName();
				Atom packageAtom = name.getPackage();

				// if there's no package.
				if (packageAtom == null)
					// it can't be in the java.util.stream package.
					return false;

				boolean fromStreamPackage = packageAtom.startsWith(STREAM_PACKAGE_ATOM);

				if (ret == null)
					// haven't decided yet. Initialize.
					ret = fromStreamPackage;
				else if (ret != fromStreamPackage)
					// we have a discrepancy.
					throw new IllegalArgumentException("Can't determine consistent write location package");
			}
		}

		return ret;
	}

	private static Collection<? extends InstanceKey> getAdditionalNecessaryReceiversFromPredecessors(
			InstanceKey instance, IClassHierarchy hierarchy, CallGraph callGraph) throws IOException, CoreException {
		Collection<InstanceKey> ret = new HashSet<>();
		LOGGER.fine(() -> "Instance is: " + instance);

		CallStringWithReceivers callString = Util.getCallString(instance);

		// for each method in the call string.
		for (IMethod calledMethod : callString.getMethods()) {
			// who's the caller?
			LOGGER.fine(() -> "Called method is: " + calledMethod);

			TypeReference evaluationType = Util.getEvaluationType(calledMethod);
			LOGGER.fine(() -> "Evaluation type is: " + evaluationType);

			boolean implementsBaseStream = Util.implementsBaseStream(evaluationType, hierarchy);
			LOGGER.fine(() -> "Is it a stream? " + implementsBaseStream);

			if (implementsBaseStream) {
				// look up the call string for this method.
				NormalAllocationInNode allocationInNode = (NormalAllocationInNode) instance;
				LOGGER.fine(() -> "Predecessor count is: " + callGraph.getPredNodeCount(allocationInNode.getNode()));

				for (Iterator<CGNode> predNodes = callGraph.getPredNodes(allocationInNode.getNode()); predNodes
						.hasNext();) {
					CGNode node = predNodes.next();
					LOGGER.fine(() -> "Found node: " + node);

					// try to get its CallStringWithReceivers.
					CallStringWithReceivers calledMethodCallString = Util.getCallString(node);

					// what are its receivers?
					Set<InstanceKey> possibleReceivers = calledMethodCallString.getPossibleReceivers();
					LOGGER.fine(() -> "It's receivers are: " + possibleReceivers);

					// filter out ones that aren't streams.
					for (InstanceKey receiver : possibleReceivers)
						if (Util.implementsBaseStream(receiver.getConcreteType().getReference(), hierarchy))
							ret.add(receiver);
				}
			}
		}
		return ret;
	}

	// FIXME: The performance of this method is not good. We should build a map
	// between block numbers and the corresponding basicBlockInContext. But,
	// when do we populate the map? In other words, we need a map of block
	// numbers to BasicBlockInContexts whose delegate's original block number
	// matches the key for a given supergraph. Should it be a table? Or, maybe
	// it's just a collection of maps, one for each supergraph? ICFGSupergraph
	// -> Map. Then, Integer -> BasicBlockInContext. But, now must also consider
	// the cgNode. Also note though that there is not a one-to-one mapping
	// between blocks and blocks in context. #21.
	/**
	 * Return the basic block in context for the given block in the procedure
	 * represented by the given call graph node in the given supergraph.
	 *
	 * @param block      The block in which to find the corresponding block in
	 *                   context in the supergraph.
	 * @param cgNode     The call graph node representing the procedure that
	 *                   contains the block.
	 * @param supergraph The supergraph in which to look up the corresponding block
	 *                   in context.
	 * @return The block in context in the given supergraph that corresponds to the
	 *         given block with the procedure represented by the given call graph
	 *         node.
	 */
	private static Optional<BasicBlockInContext<IExplodedBasicBlock>> getBasicBlockInContextForBlock(
			ISSABasicBlock block, CGNode cgNode, ICFGSupergraph supergraph) {
		// can we search the supergraph for the corresponding block? Do I need
		// to search the entire graph?
		for (BasicBlockInContext<IExplodedBasicBlock> basicBlockInContext : supergraph) {
			CGNode blockInContextProcedure = supergraph.getProcOf(basicBlockInContext);
			if (blockInContextProcedure == cgNode) {
				IExplodedBasicBlock delegate = basicBlockInContext.getDelegate();
				if (!delegate.isEntryBlock() && !delegate.isExitBlock()
						&& delegate.getOriginalNumber() == block.getNumber() && delegate.getInstruction() != null)
					return Optional.of(basicBlockInContext);
			}
		}
		return Optional.empty();
	}

	private static boolean isStatefulIntermediateOperation(MethodReference method) {
		return signatureMatches(STATEFUL_INTERMEDIATE_OPERATIONS, method);
	}

	private static boolean isStreamCreatedFromIntermediateOperation(InstanceKey instance, IClassHierarchy hierarchy,
			CallGraph callGraph) throws IOException, CoreException {
		// Get the immediate possible receivers of the stream instance.
		Set<InstanceKey> receivers = Util.getCallString(instance).getPossibleReceivers();

		// Get any additional receivers we need to consider.
		Collection<? extends InstanceKey> additionalReceivers = getAdditionalNecessaryReceiversFromPredecessors(
				instance, hierarchy, callGraph);

		// Add them to the receivers set.
		receivers.addAll(additionalReceivers);

		if (receivers.isEmpty())
			return false;

		// each receiver must be a stream.
		return receivers.stream().allMatch(r -> {
			IClass type = r.getConcreteType();
			return edu.cuny.hunter.streamrefactoring.core.analysis.Util.isBaseStream(type)
					|| type.getAllImplementedInterfaces().stream().anyMatch(Util::isBaseStream);
		});
	}

	private static boolean isTerminalOperation(MethodReference method) {
		return signatureMatches(TERMINAL_OPERATIONS, method);
	}

	private static boolean isTerminalOperationWhereReduceOrderDoesNotMatter(MethodReference method) {
		return signatureMatches(TERMINAL_OPERATIONS_WHERE_REDUCE_ORDERING_DOES_NOT_MATTER, method);
	}

	private static boolean isTerminalOperationWhereReduceOrderMatters(MethodReference method) {
		return signatureMatches(TERMINAL_OPERATIONS_WERE_REDUCE_ORDERING_MATTERS, method);
	}

	private static boolean isTerminalOperationWhereReduceOrderMattersIsUnknown(MethodReference method) {
		return signatureMatches(TERMINAL_OPERATIONS_WHERE_REDUCE_ORDERING_MATTERS_IS_UNKNOWN, method);
	}

	private static boolean isVoid(Collection<TypeAbstraction> types) {
		return types.stream().map(TypeAbstraction::getTypeReference).allMatch(tr -> tr.equals(TypeReference.Void));
	}

	private static Collection<? extends IDFAState> mergeTypeStates(Set<IDFAState> set1, Set<IDFAState> set2) {
		if (set1.isEmpty())
			return set2;
		else if (set2.isEmpty())
			return set1;

		Set<IDFAState> ret = new HashSet<>();

		for (IDFAState state1 : set1)
			for (IDFAState state2 : set2)
				ret.add(selectState(state1, state2));

		return ret;
	}

	private static void outputTypeStateStatistics(AggregateSolverResult result) {
		LOGGER.info("Total instances: " + result.totalInstancesNum());
		LOGGER.info("Processed instances: " + result.processedInstancesNum());
		LOGGER.info("Skipped instances: " + result.skippedInstances());
	}

	private static IDFAState selectState(IDFAState state1, IDFAState state2) {
		if (state1.getName().equals(BOTTOM_STATE_NAME))
			return state2;
		else
			return state1;
	}

	private static boolean signatureMatches(String[] operations, MethodReference method) {
		String signature = method.getSignature();
		return Arrays.stream(operations).map(o -> o + "(").anyMatch(signature::startsWith);
	}

	/**
	 * A table mapping an instance and a block to the instance's possible states at
	 * that block.
	 */
	private Table<InstanceKey, BasicBlockInContext<IExplodedBasicBlock>, Map<TypestateRule, Set<IDFAState>>> instanceBlockStateTable = HashBasedTable
			.create();

	/**
	 * A set of instances whose reduce ordering may matter.
	 */
	private Set<InstanceKey> instancesWhoseReduceOrderingPossiblyMatters = new HashSet<>();

	private Set<InstanceKey> instancesWithoutTerminalOperations = new HashSet<>();

	/**
	 * A set of instances whose pipelines contain behavioral parameters that may
	 * have side-effects.
	 */
	private Set<InstanceKey> instancesWithSideEffects = new HashSet<>();

	/**
	 * All of the stream's predecessors.
	 */
	private Map<InstanceKey, Set<InstanceKey>> instanceToAllPredecessorsMap = new HashMap<>();

	/**
	 * A stream's immediate predecessor.
	 */
	private Map<InstanceKey, Set<InstanceKey>> instanceToPredecessorsMap = new HashMap<>();

	/**
	 * Instances whose pipelines may contain stateful intermediate operations.
	 */
	private Map<InstanceKey, Boolean> instanceToStatefulIntermediateOperationContainment = new HashMap<>();

	private Map<InstanceKey, Stream> instanceToStreamMap = new HashMap<>();

	private Map<InstanceKey, Map<TypestateRule, Set<IDFAState>>> originStreamToMergedTypeStateMap = new HashMap<>();

	private Map<BasicBlockInContext<IExplodedBasicBlock>, OrdinalSet<InstanceKey>> terminalBlockToPossibleReceivers = new HashMap<>();

	private Set<InstanceKey> trackedInstances = new HashSet<>();

	private Set<IDFAState> computeMergedTypeState(InstanceKey instanceKey,
			BasicBlockInContext<IExplodedBasicBlock> block, StreamAttributeTypestateRule rule) {
		Set<InstanceKey> predecessors = this.instanceToPredecessorsMap.get(instanceKey);
		Map<TypestateRule, Set<IDFAState>> ruleToStates = this.instanceBlockStateTable.get(instanceKey, block);

		if (ruleToStates == null)
			return Collections.emptySet();

		Set<IDFAState> possibleInstanceStates = ruleToStates.get(rule);

		if (predecessors.isEmpty())
			return possibleInstanceStates;

		Set<IDFAState> ret = new HashSet<>();
		for (InstanceKey pred : predecessors)
			ret.addAll(mergeTypeStates(possibleInstanceStates, this.computeMergedTypeState(pred, block, rule)));

		return ret;
	}

	// TODO: This should probably be cached.
	private Set<InstanceKey> computePossibleOriginStreams(InstanceKey instanceKey) {
		// if there is no instance.
		if (instanceKey == null)
			// there are no origins.
			return Collections.emptySet();

		// otherwise, retrieve the predecessors of the instance.
		Set<InstanceKey> predecessors = this.instanceToPredecessorsMap.get(instanceKey);

		// if there are no predecessors for this instance.
		if (predecessors.isEmpty())
			// then this instance must be its own origin.
			return Collections.singleton(instanceKey);

		// otherwise, we have a situation where the instance in question has one
		// or more predecessors.
		// In this case, the possible origins of the given instance are the
		// possible origins of each of its predecessors.
		return predecessors.stream().map(this::computePossibleOriginStreams).flatMap(os -> os.stream())
				.collect(Collectors.toSet());
	}

	private boolean deriveRomForNonScalarMethod(Collection<TypeAbstraction> possibleReturnTypes,
			OrderingInference orderingInference)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		Ordering ordering;
		try {
			ordering = orderingInference.inferOrdering(possibleReturnTypes);
		} catch (InconsistentPossibleOrderingException e) {
			// default to ordered #55.
			ordering = Ordering.ORDERED;
			LOGGER.log(Level.WARNING, "Inconsistently ordered possible return types encountered: " + possibleReturnTypes
					+ ". Defaulting to: " + ordering, e);
		}

		LOGGER.info("Ordering of reduction type is: " + ordering);

		// if we can't find the ordering.
		if (ordering == null) {
			// default to ordered.
			ordering = Ordering.ORDERED;
			LOGGER.warning("Can't determine ordering for possible return types: " + possibleReturnTypes
					+ ". Defaulting to: " + ordering);
		}

		switch (ordering) {
		case UNORDERED:
			return false;
		case ORDERED:
			return true;
		default:
			throw new IllegalStateException("Logic missing ordering.");
		}
	}

	private void discoverIfReduceOrderingPossiblyMatters(EclipseProjectAnalysisEngine<InstanceKey> engine,
			OrderingInference orderingInference, IProgressMonitor monitor) throws UTFDataFormatException,
			JavaModelException, NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		monitor.beginTask("Discovering if reduce order matters...",
				this.terminalBlockToPossibleReceivers.keySet().size());

		// for each terminal operation call, I think?
		for (BasicBlockInContext<IExplodedBasicBlock> block : this.terminalBlockToPossibleReceivers.keySet()) {
			int processedInstructions = 0;
			for (SSAInstruction instruction : block) {
				// if it's a phi instruction.
				if (instruction instanceof SSAPhiInstruction)
					continue;

				assert instruction instanceof SSAInvokeInstruction : "Expecting SSAInvokeInstruction, was: "
						+ instruction.getClass();
				SSAInvokeInstruction invokeInstruction = (SSAInvokeInstruction) instruction;

				int numOfRetVals = invokeInstruction.getNumberOfReturnValues();
				assert numOfRetVals <= 1 : "How could you possibly return " + numOfRetVals + " values?";

				Collection<TypeAbstraction> possibleReturnTypes = null;

				// if it's a non-void method.
				if (numOfRetVals > 0) {
					int returnValue = invokeInstruction.getReturnValue(0);

					possibleReturnTypes = Util.getPossibleTypesInterprocedurally(Collections.singleton(block.getNode()),
							returnValue, engine, orderingInference);

					LOGGER.fine("Possible reduce types are: " + possibleReturnTypes);
				} else
					// it's a void method.
					possibleReturnTypes = Collections.singleton(JavaPrimitiveType.VOID);

				Boolean rom = null;

				try {
					if (isVoid(possibleReturnTypes))
						rom = deriveRomForVoidMethod(invokeInstruction);
					else {
						boolean scalar = Util.isScalar(possibleReturnTypes);
						if (scalar)
							rom = deriveRomForScalarMethod(invokeInstruction);
						else // !scalar
							rom = this.deriveRomForNonScalarMethod(possibleReturnTypes, orderingInference);
					}
				} catch (UnknownIfReduceOrderMattersException e) {
					// for each possible receiver associated with the terminal block.
					OrdinalSet<InstanceKey> receivers = this.terminalBlockToPossibleReceivers.get(block);

					for (InstanceKey instanceKey : receivers) {
						// get the stream for the instance key.
						Set<InstanceKey> originStreams = this.computePossibleOriginStreams(instanceKey);

						// for each origin stream.
						for (InstanceKey origin : originStreams) {
							// get the "Stream" representing it.
							Stream stream = this.instanceToStreamMap.get(origin);

							if (stream == null)
								LOGGER.warning(() -> "Can't find Stream instance for instance key: " + instanceKey
										+ " using origin: " + origin);
							else {
								LOGGER.log(Level.WARNING, "Unable to derive ROM for: " + stream.getCreation(), e);
								stream.addStatusEntry(PreconditionFailure.NON_DETERMINABLE_REDUCTION_ORDERING,
										"Cannot derive reduction ordering for stream: " + stream.getCreation() + ".");
							}
						}
					}
				}

				// if reduce ordering matters.
				if (rom != null)
					if (rom) {
						LOGGER.fine(() -> "Reduce ordering matters for: " + invokeInstruction);
						OrdinalSet<InstanceKey> possibleReceivers = this.terminalBlockToPossibleReceivers.get(block);
						possibleReceivers.forEach(this.instancesWhoseReduceOrderingPossiblyMatters::add);
					} else
						// otherwise, just log.
						LOGGER.fine(() -> "Reduce ordering doesn't matter for: " + invokeInstruction);

				++processedInstructions;
			}
			assert processedInstructions == 1 : "Expecting to process one and only one instruction here.";
			monitor.worked(1);
		}
	}

	private void discoverLambdaSideEffects(EclipseProjectAnalysisEngine<InstanceKey> engine,
			Map<CGNode, OrdinalSet<PointerKey>> mod, Iterable<InstanceKey> instances,
			MethodReference declaredTargetOfCaller, IR ir, int use) {
		// look up it's definition.
		DefUse defUse = engine.getCache().getDefUse(ir);
		// it should be a call.
		SSAInstruction def = defUse.getDef(use);

		// if we found it.
		if (def != null)
			if (def instanceof SSAAbstractInvokeInstruction) {
				SSAAbstractInvokeInstruction instruction = (SSAAbstractInvokeInstruction) def;

				// take a look at the nodes in the caller.
				Set<CGNode> nodes = engine.getCallGraph().getNodes(declaredTargetOfCaller);

				// for each caller node.
				for (CGNode cgNode : nodes)
					// for each call site.
					for (Iterator<CallSiteReference> callSiteIt = cgNode.iterateCallSites(); callSiteIt.hasNext();) {
						CallSiteReference callSiteReference = callSiteIt.next();

						// if the call site is the as the one in the
						// behavioral parameter definition.
						if (callSiteReference.equals(instruction.getCallSite())) {
							// look up the possible target nodes of the call
							// site from the caller.
							Set<CGNode> possibleTargets = engine.getCallGraph().getPossibleTargets(cgNode,
									callSiteReference);
							LOGGER.fine(() -> "#possible targets: " + possibleTargets.size());

							if (!possibleTargets.isEmpty())
								LOGGER.fine(() -> possibleTargets.stream().map(String::valueOf)
										.collect(Collectors.joining("\n", "Possible target: ", "")));

							// for each possible target node.
							for (CGNode target : possibleTargets) {
								// get the set of pointers (locations) it
								// may modify
								OrdinalSet<PointerKey> modSet = mod.get(target);
								LOGGER.fine(() -> "#original modified locations: " + modSet.size());

								Collection<PointerKey> filteredModSet = new HashSet<>();

								for (PointerKey pointerKey : modSet)
									if (!filterPointerKey(pointerKey, engine))
										filteredModSet.add(pointerKey);

								LOGGER.fine(() -> "#filtered modified locations: " + filteredModSet.size());

								// if it's non-empty.
								if (!filteredModSet.isEmpty()) {
									filteredModSet
											.forEach(pk -> LOGGER.fine(() -> "Filtered modified location: " + pk));

									// mark the instances whose pipeline may
									// have side-effects.
									instances.forEach(this.instancesWithSideEffects::add);
								}
							}
							// we found a match between the graph call site
							// and the one in the definition. No need to
							// continue.
							break;
						}
					}
			} else
				LOGGER.warning("Def was an instance of a: " + def.getClass());
	}

	private void discoverPossibleSideEffects(EclipseProjectAnalysisEngine<InstanceKey> engine, IProgressMonitor monitor)
			throws IOException, CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, "Discovering side-effects...", 100);

		// create the ModRef analysis.
		ModRef<InstanceKey> modRef = ModRef.make();

		// compute modifications over the call graph.
		// TODO: Should this be cached? Didn't have luck caching the call graph.
		// Perhaps this will be similar.
		Map<CGNode, OrdinalSet<PointerKey>> mod = modRef.computeMod(engine.getCallGraph(), engine.getPointerAnalysis());

		// for each terminal operation call, I think?
		SubMonitor loopMonitor = subMonitor.split(50, SubMonitor.SUPPRESS_NONE)
				.setWorkRemaining(this.terminalBlockToPossibleReceivers.keySet().size());

		for (BasicBlockInContext<IExplodedBasicBlock> block : this.terminalBlockToPossibleReceivers.keySet()) {
			int processedInstructions = 0;
			for (SSAInstruction instruction : block) {
				// if it's a phi instruction.
				if (instruction instanceof SSAPhiInstruction)
					continue;

				// number of uses should be 2 for a single explicit param.
				int numberOfUses = instruction.getNumberOfUses();

				for (int i = 1; i < numberOfUses; i++) {
					// get an explicit parameter use.
					int paramUse = instruction.getUse(i);
					IR ir = engine.getCache().getIR(block.getMethod());

					// expecting an invoke instruction here.
					assert instruction instanceof SSAInvokeInstruction : "Expecting invoke instruction.";

					// get a reference to the calling method.
					MethodReference declaredTarget = block.getMethod().getReference();

					this.discoverLambdaSideEffects(engine, mod, this.terminalBlockToPossibleReceivers.get(block),
							declaredTarget, ir, paramUse);
				}
				++processedInstructions;
			}

			assert processedInstructions == 1 : "Expecting to process one and only one instruction here.";
			loopMonitor.worked(1);
		}

		// for each instance in the analysis result (these should be the
		// "intermediate" streams).
		loopMonitor = subMonitor.split(50, SubMonitor.SUPPRESS_NONE).setWorkRemaining(this.trackedInstances.size());

		for (InstanceKey instance : this.trackedInstances) {
			// make sure that the stream is the result of an intermediate
			// operation.
			if (!isStreamCreatedFromIntermediateOperation(instance, engine.getClassHierarchy(), engine.getCallGraph()))
				continue;

			CallStringWithReceivers callString = Util.getCallString(instance);
			assert callString.getMethods().length >= 1 : "Expecting call sites at least one-deep.";

			IR ir = engine.getCache().getIR(callString.getMethods()[0]);

			if (ir == null) {
				LOGGER.warning("Can't find IR for target: " + callString.getMethods()[0]);
				continue; // next instance.
			}

			// get calls to the caller target.
			SSAAbstractInvokeInstruction[] calls = ir.getCalls(callString.getCallSiteRefs()[0]);
			assert calls.length == 1 : "Are we only expecting one call here?";

			// I guess we're only interested in ones with a single behavioral
			// parameter (the first parameter is implicit).
			if (calls[0].getNumberOfUses() == 2) {
				// get the use of the first parameter.
				int use = calls[0].getUse(1);
				this.discoverLambdaSideEffects(engine, mod, Collections.singleton(instance),
						callString.getMethods()[0].getReference(), ir, use);
			}

			loopMonitor.worked(1);
		}
	}

	private void discoverPossibleStatefulIntermediateOperations(IClassHierarchy hierarchy, CallGraph callGraph,
			IProgressMonitor monitor) throws IOException, CoreException {
		monitor.beginTask("Discovering stateful intermediate operations...", this.trackedInstances.size());

		// for each instance in the analysis result (these should be the
		// "intermediate" streams).
		for (InstanceKey instance : this.trackedInstances) {
			if (!this.instanceToStatefulIntermediateOperationContainment.containsKey(instance)) {
				// make sure that the stream is the result of an intermediate
				// operation.
				if (!isStreamCreatedFromIntermediateOperation(instance, hierarchy, callGraph))
					continue;

				CallStringWithReceivers callString = Util.getCallString(instance);

				boolean found = false;
				for (CallSiteReference callSiteReference : callString.getCallSiteRefs())
					if (isStatefulIntermediateOperation(callSiteReference.getDeclaredTarget())) {
						found = true; // found one.
						break; // no need to continue checking.
					}
				this.instanceToStatefulIntermediateOperationContainment.put(instance, found);
			}
			monitor.worked(1);
		}
	}

	private void discoverTerminalOperations(IProgressMonitor monitor) {
		Collection<OrdinalSet<InstanceKey>> receiverSetsThatHaveTerminalOperations = this.terminalBlockToPossibleReceivers
				.values();

		// This will be the OK set.
		Collection<InstanceKey> validStreams = new HashSet<>();

		// Now, we need to flatten the receiver sets.
		monitor.beginTask("Flattening...", receiverSetsThatHaveTerminalOperations.size());
		for (OrdinalSet<InstanceKey> receiverSet : receiverSetsThatHaveTerminalOperations) {
			// for each receiver set
			for (InstanceKey instance : receiverSet)
				// add it to the OK set.
				validStreams.add(instance);
			monitor.worked(1);
		}

		// Now, we have the OK set. Let's propagate it.
		this.propagateStreamInstanceProperty(validStreams);

		// Now, we will find the set containing all stream instances.
		Set<InstanceKey> allStreamInstances = new HashSet<>(this.trackedInstances);

		// Let's now find the bad set.
		allStreamInstances.removeAll(validStreams);
		Set<InstanceKey> badStreamInstances = allStreamInstances;

		this.instancesWithoutTerminalOperations.addAll(badStreamInstances);
	}

	private void fillInstanceToPredecessorMap(EclipseProjectAnalysisEngine<InstanceKey> engine)
			throws IOException, CoreException {
		for (InstanceKey instance : this.trackedInstances) {
			CallStringWithReceivers callString = Util.getCallString(instance);
			Set<InstanceKey> possibleReceivers = new HashSet<>(callString.getPossibleReceivers());

			// get any additional receivers if necessary #36.
			Collection<? extends InstanceKey> additionalNecessaryReceiversFromPredecessors = getAdditionalNecessaryReceiversFromPredecessors(
					instance, engine.getClassHierarchy(), engine.getCallGraph());
			LOGGER.fine(() -> "Adding additional receivers: " + additionalNecessaryReceiversFromPredecessors);
			possibleReceivers.addAll(additionalNecessaryReceiversFromPredecessors);

			this.instanceToPredecessorsMap.merge(instance, possibleReceivers, (x, y) -> {
				x.addAll(y);
				return x;
			});
		}
	}

	private void fillInstanceToStreamMap(Set<Stream> streamSet, EclipseProjectAnalysisEngine<InstanceKey> engine,
			IProgressMonitor monitor) throws InvalidClassFileException, IOException, CoreException {
		monitor.beginTask("Propagating...", streamSet.size());
		int skippedStreams = 0;
		for (Stream stream : streamSet) {
			InstanceKey instanceKey = null;
			try {
				instanceKey = stream.getInstanceKey(this.trackedInstances, engine);
			} catch (InstanceKeyNotFoundException e) {
				LOGGER.log(Level.WARNING,
						"Encountered unreachable code while processing: " + stream.getCreation() + ".", e);
				stream.addStatusEntry(PreconditionFailure.STREAM_CODE_NOT_REACHABLE,
						"Either pivital code isn't reachable for stream: " + stream.getCreation()
								+ " or entry points are misconfigured.");
				++skippedStreams;
				continue; // next stream.
			} catch (UnhandledCaseException e) {
				String msg = "Encountered possible unhandled case (AIC #155) while processing: " + stream.getCreation()
						+ ".";
				LOGGER.log(Level.WARNING, msg, e);
				stream.addStatusEntry(PreconditionFailure.CURRENTLY_NOT_HANDLED, msg);
				++skippedStreams;
				continue; // next stream.
			} catch (NoApplicationCodeExistsInCallStringsException e) {
				LOGGER.log(Level.WARNING, "Did not encounter application code in call strings while processing: "
						+ stream.getCreation() + ".", e);
				stream.addStatusEntry(PreconditionFailure.NO_APPLICATION_CODE_IN_CALL_STRINGS,
						"No application code in the call strings generated for stream: " + stream.getCreation()
								+ " was found. The maximum call string length (" + engine.getNToUseForStreams()
								+ ") may need to be increased.");
				++skippedStreams;
				continue; // next stream.
			}

			// add the mapping.
			Stream oldValue = this.instanceToStreamMap.put(instanceKey, stream);

			// if mapping a different value.
			if (oldValue != null && oldValue != stream)
				LOGGER.warning("Reassociating stream: " + stream.getCreation() + " with: " + instanceKey
						+ ". Old stream was: " + oldValue.getCreation() + ".");

			monitor.worked(1);
		} // end each stream.

		// sanity check since it's a bijection.
		if (this.instanceToStreamMap.keySet().size() != streamSet.size() - skippedStreams)
			LOGGER.warning("Stream set of size: " + (streamSet.size() - skippedStreams)
					+ " does not produce a bijection of instance keys of size: "
					+ this.instanceToStreamMap.keySet().size() + ".");
	}

	private Set<InstanceKey> getAllPredecessors(InstanceKey instanceKey) {
		if (!this.instanceToAllPredecessorsMap.containsKey(instanceKey)) {
			Set<InstanceKey> ret = new HashSet<>();

			// add the instance's predecessors.
			ret.addAll(this.instanceToPredecessorsMap.get(instanceKey));

			// add their predecessors.
			ret.addAll(this.instanceToPredecessorsMap.get(instanceKey).stream()
					.flatMap(ik -> this.getAllPredecessors(ik).stream()).collect(Collectors.toSet()));

			this.instanceToAllPredecessorsMap.put(instanceKey, ret);
			return ret;
		} else
			return this.instanceToAllPredecessorsMap.get(instanceKey);
	}

	public Collection<IDFAState> getStates(StreamAttributeTypestateRule rule, InstanceKey instanceKey) {
		Map<TypestateRule, Set<IDFAState>> mergedTypeState = this.originStreamToMergedTypeStateMap.get(instanceKey);

		if (mergedTypeState == null) {
			LOGGER.warning(
					() -> "Can't find merged type state for rule: " + rule + " and instance key: " + instanceKey);
			return Collections.emptySet();
		}

		return mergedTypeState.get(rule);
	}

	public Collection<InstanceKey> getTrackedInstances() {
		return Collections.unmodifiableCollection(this.trackedInstances);
	}

	private void propagateStreamInstanceProperty(Collection<InstanceKey> streamInstancesWithProperty) {
		streamInstancesWithProperty.addAll(streamInstancesWithProperty.stream()
				.flatMap(ik -> this.getAllPredecessors(ik).stream()).collect(Collectors.toSet()));
	}

	public Map<TypestateRule, Statistics> start(Set<Stream> streamSet, EclipseProjectAnalysisEngine<InstanceKey> engine,
			OrderingInference orderingInference, IProgressMonitor monitor)
			throws PropertiesException, CancelException, IOException, CoreException, NoniterableException,
			NoninstantiableException, CannotExtractSpliteratorException, InvalidClassFileException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, "Performing typestate analysis (may take a while)", 100);
		Map<TypestateRule, Statistics> ret = new HashMap<>();

		CallGraph prunedCallGraph = pruneCallGraph(engine.getCallGraph(), engine.getClassHierarchy());
		BenignOracle ora = new ModifiedBenignOracle(prunedCallGraph, engine.getPointerAnalysis());

		PropertiesManager manager = PropertiesManager.initFromMap(Collections.emptyMap());
		PropertiesManager.registerProperties(
				new PropertiesManager.IPropertyDescriptor[] { WholeProgramProperties.Props.LIVE_ANALYSIS });
		TypeStateOptions typeStateOptions = new TypeStateOptions(manager);
		typeStateOptions.setBooleanValue(WholeProgramProperties.Props.LIVE_ANALYSIS.getName(), false);
		// TODO: #127 should also set entry points.

		TypeReference typeReference = TypeReference.findOrCreate(ClassLoaderReference.Primordial,
				"Ljava/util/stream/BaseStream");
		IClass streamClass = engine.getClassHierarchy().lookupClass(typeReference);

		StreamAttributeTypestateRule[] ruleArray = createStreamAttributeTypestateRules(streamClass);

		// for each rule.
		SubMonitor ruleMonitor = subMonitor.split(70, SubMonitor.SUPPRESS_NONE).setWorkRemaining(ruleArray.length);

		for (StreamAttributeTypestateRule rule : ruleArray) {
			// create a DFA based on the rule.
			TypeStateProperty dfa = new TypeStateProperty(rule, engine.getClassHierarchy());

			// this gets a solver that tracks all streams.
			LOGGER.info(() -> "Starting " + rule.getName() + " solver for: " + engine.getProject().getElementName());
			ISafeSolver solver = TypestateSolverFactory.getSolver(engine.getOptions(), prunedCallGraph,
					engine.getPointerAnalysis(), engine.getHeapGraph(), dfa, ora, typeStateOptions, null, null, null);

			AggregateSolverResult result;
			try {
				result = (AggregateSolverResult) solver.perform(ruleMonitor.split(50, SubMonitor.SUPPRESS_NONE));
			} catch (SolverTimeoutException | MaxFindingsException | SetUpException | WalaException e) {
				throw new RuntimeException("Exception caught during typestate analysis.", e);
			}

			// record typestate statistics.
			outputTypeStateStatistics(result);

			Statistics lastStatistics = ret.put(rule,
					new Statistics(result.processedInstancesNum(), result.skippedInstances()));
			assert lastStatistics == null : "Reassociating statistics.";

			// for each instance in the typestate analysis result.
			SubMonitor instanceMonitor = ruleMonitor.split(20, SubMonitor.SUPPRESS_NONE)
					.setWorkRemaining(result.totalInstancesNum());

			for (Iterator<InstanceKey> iterator = result.iterateInstances(); iterator.hasNext();) {
				// get the instance's key.
				InstanceKey instanceKey = iterator.next();

				// add to tracked instances.
				this.trackedInstances.add(instanceKey);

				// get the result for that instance.
				TypeStateResult instanceResult = (TypeStateResult) result.getInstanceResult(instanceKey);

				// get the supergraph for the instance result.
				ICFGSupergraph supergraph = instanceResult.getSupergraph();

				// TODO: Can this be somehow rewritten to get blocks corresponding to terminal
				// operations?
				// for each call graph node in the call graph.
				for (CGNode cgNode : prunedCallGraph)
					// separating client from library code, improving performance #103.
					if (cgNode.getMethod().getDeclaringClass().getClassLoader().getReference()
							.equals(ClassLoaderReference.Application)) {

						// we can verify that only client nodes are being considered
						LOGGER.fine(() -> "Examining client call graph node: " + cgNode);

						// for each call site in the call graph node.
						for (Iterator<CallSiteReference> callSites = cgNode.iterateCallSites(); callSites.hasNext();) {
							// get the call site reference.
							CallSiteReference callSiteReference = callSites.next();

							// get the (declared) called method at the call site.
							MethodReference calledMethod = callSiteReference.getDeclaredTarget();

							// is it a terminal operation? TODO: Should this be
							// cached somehow? Collection of all terminal operation
							// invocations?
							if (isTerminalOperation(calledMethod)) {
								// get the basic block for the call.
								IR ir = cgNode.getIR();

								ISSABasicBlock[] blocksForCall = ir.getBasicBlocksForCall(callSiteReference);

								assert blocksForCall.length == 1 : "Expecting only a single basic block for the call: "
										+ callSiteReference;

								for (ISSABasicBlock block : blocksForCall) {
									BasicBlockInContext<IExplodedBasicBlock> blockInContext = getBasicBlockInContextForBlock(
											block, cgNode, supergraph)
													.orElseThrow(() -> new IllegalStateException(
															"No basic block in context for block: " + block));

									if (!this.terminalBlockToPossibleReceivers.containsKey(blockInContext)) {
										// associate possible receivers with the
										// blockInContext.
										// search through each instruction in the
										// block.
										int processedInstructions = 0;

										for (SSAInstruction instruction : block) {
											// if it's not an invoke instruction.
											if (!(instruction instanceof SSAAbstractInvokeInstruction))
												// skip it. Phi instructions will be handled by the pointer analysis
												// below.
												continue;

											// Get the possible receivers. This
											// number corresponds to the value
											// number of the receiver of the method.
											int valueNumberForReceiver = instruction.getUse(0);

											// it should be represented by a pointer
											// key.
											PointerKey pointerKey = engine.getHeapGraph().getHeapModel()
													.getPointerKeyForLocal(cgNode, valueNumberForReceiver);

											// get the points to set for the
											// receiver. This will give us all
											// object instances that the receiver
											// reference points to.
											OrdinalSet<InstanceKey> pointsToSet = engine.getPointerAnalysis()
													.getPointsToSet(pointerKey);
											assert pointsToSet != null : "The points-to set (I think) should not be null for pointer: "
													+ pointerKey;

											OrdinalSet<InstanceKey> previousReceivers = this.terminalBlockToPossibleReceivers
													.put(blockInContext, pointsToSet);
											assert previousReceivers == null : "Reassociating a blockInContext: "
													+ blockInContext + " with a new points-to set: " + pointsToSet
													+ " that was originally: " + previousReceivers;

											++processedInstructions;
										}

										assert processedInstructions == 1 : "Expecting to process one and only one instruction here.";
									}

									IntSet resultingFacts = instanceResult.getResult().getResult(blockInContext);
									for (IntIterator factIterator = resultingFacts.intIterator(); factIterator
											.hasNext();) {
										int fact = factIterator.next();

										// retrieve the state set for this instance
										// and block.
										Map<TypestateRule, Set<IDFAState>> ruleToStates = this.instanceBlockStateTable
												.get(instanceKey, blockInContext);

										// if it doesn't yet exist.
										if (ruleToStates == null) {
											// allocate a new rule map.
											ruleToStates = new HashMap<>();

											// place it in the table.
											this.instanceBlockStateTable.put(instanceKey, blockInContext, ruleToStates);
										}

										Set<IDFAState> stateSet = ruleToStates.get(rule);

										// if it does not yet exist.
										if (stateSet == null) {
											// allocate a new set.
											stateSet = new HashSet<>();

											// place it in the map.
											ruleToStates.put(rule, stateSet);
										}

										// get the facts.
										Factoid factoid = instanceResult.getDomain().getMappedObject(fact);
										if (factoid != DUMMY_ZERO) {
											BaseFactoid baseFactoid = (BaseFactoid) factoid;
											assert baseFactoid.instance.equals(
													instanceKey) : "Sanity check that the fact instance should be the same as the instance being examined.";

											// add the encountered state to the set.
											LOGGER.fine(() -> "Adding state: " + baseFactoid.state + " for instance: "
													+ baseFactoid.instance + " for block: " + block + " for rule: "
													+ rule.getName());
											stateSet.add(baseFactoid.state);
										}
									}
								}
							}
						}
					}
				instanceMonitor.worked(1);
			} // end for each instance in the typestate analysis result.

			// fill the instance to predecessors map if it's empty.
			if (this.instanceToPredecessorsMap.isEmpty())
				this.fillInstanceToPredecessorMap(engine);

			// for each terminal operation call.
			for (BasicBlockInContext<IExplodedBasicBlock> block : this.terminalBlockToPossibleReceivers.keySet()) {
				OrdinalSet<InstanceKey> possibleReceivers = this.terminalBlockToPossibleReceivers.get(block);
				// for each possible receiver of the terminal operation call.
				for (InstanceKey instanceKey : possibleReceivers) {
					Set<IDFAState> possibleStates = this.computeMergedTypeState(instanceKey, block, rule);
					Set<InstanceKey> possibleOriginStreams = this.computePossibleOriginStreams(instanceKey);
					possibleOriginStreams.forEach(os -> {
						// create a new map.
						Map<TypestateRule, Set<IDFAState>> ruleToStates = new HashMap<>();
						ruleToStates.put(rule, new HashSet<>(possibleStates));

						// merge it.
						this.originStreamToMergedTypeStateMap.merge(os, ruleToStates, (m1, m2) -> {
							Set<IDFAState> states1 = m1.get(rule);
							Set<IDFAState> states2 = m2.get(rule);

							// if the states in for this rule are empty.
							if (states1 == null) {
								// create a new set.
								states1 = new HashSet<>();

								// put it in the map.
								m1.put(rule, states1);
							}

							// since we're merging the second map into the
							// first, nothing to do if the second map is empty.
							if (states2 != null)
								states1.addAll(states2);

							// finally, return the second map.
							return m1;
						});
					});
				}
			}
			ruleMonitor.worked(1);
		} // end for each rule.

		// create a mapping between stream instances (from the analysis) and stream
		// objects (from the refactoring).
		this.fillInstanceToStreamMap(streamSet, engine, subMonitor.split(5, SubMonitor.SUPPRESS_NONE));

		this.discoverTerminalOperations(subMonitor.split(5, SubMonitor.SUPPRESS_NONE));

		// fill the instance side-effect set.
		this.discoverPossibleSideEffects(engine, subMonitor.split(5, SubMonitor.SUPPRESS_NONE));

		// discover whether any stateful intermediate operations are
		// present.
		this.discoverPossibleStatefulIntermediateOperations(engine.getClassHierarchy(), prunedCallGraph,
				subMonitor.split(5, SubMonitor.SUPPRESS_NONE));

		// does reduction order matter?
		this.discoverIfReduceOrderingPossiblyMatters(engine, orderingInference,
				subMonitor.split(5, SubMonitor.SUPPRESS_NONE));

		// propagate the instances with side-effects.
		this.propagateStreamInstanceProperty(this.instancesWithSideEffects);

		// propagate the instances with stateful intermediate operations.
		BinaryOperator<Boolean> remappingFunction = (v1, v2) -> {
			// if they're the same value, just use the first one.
			if (v1 == v2)
				return v2;
			else
				// otherwise, if we have either previously seen an SIO
				// or see one now, we should remember that.
				return true;
		};

		this.instanceToStatefulIntermediateOperationContainment.entrySet().stream().filter(Entry::getValue)
				.map(Entry::getKey).flatMap(ik -> this.getAllPredecessors(ik).stream())
				.collect(Collectors.toMap(Function.identity(), v -> true, remappingFunction)).forEach((k,
						v) -> this.instanceToStatefulIntermediateOperationContainment.merge(k, v, remappingFunction));

		// propagate the instances whose reduce ordering possibly matters.
		this.propagateStreamInstanceProperty(this.instancesWhoseReduceOrderingPossiblyMatters);

		// for stream instance key.
		for (InstanceKey streamInstanceKey : this.instanceToStreamMap.keySet()) {
			Stream stream = this.instanceToStreamMap.get(streamInstanceKey);

			// determine possible side-effects.
			stream.setHasPossibleSideEffects(this.instancesWithSideEffects.contains(streamInstanceKey));

			// determine if the stream has possible stateful intermediate operations.
			stream.setHasPossibleStatefulIntermediateOperations(
					this.instanceToStatefulIntermediateOperationContainment.getOrDefault(streamInstanceKey, false));

			// determine if the stream is not associated with a terminal operation.
			stream.setHasNoTerminalOperation(this.instancesWithoutTerminalOperations.contains(streamInstanceKey));

			// if the stream is terminated.
			if (!stream.hasNoTerminalOperation()) {
				// assign states to the current stream for each typestate rule.
				for (StreamAttributeTypestateRule rule : ruleArray) {
					// get the states.
					Collection<IDFAState> states = this.getStates(rule, streamInstanceKey);

					// Map IDFAState to StreamExecutionMode, etc., and add them to the
					// possible stream states but only if they're not bottom (for those,
					// we fall back to the initial state).
					rule.addPossibleAttributes(stream, states);
				} // for each rule.

				// determine if the stream reduce ordering possibly matters.
				stream.setReduceOrderingPossiblyMatters(
						this.instancesWhoseReduceOrderingPossiblyMatters.contains(streamInstanceKey));
			}
		}
		return ret;
	}

	/**
	 * This method is used to prune call graph. For each CGNode in the callGraph, it
	 * check whether it is a stream node. If it is, then keep it. If it not, then
	 * remove it.
	 * 
	 * @param callGraph
	 * @param classHierarchy
	 * @return A pruned callGraph
	 */
	private static CallGraph pruneCallGraph(CallGraph callGraph, IClassHierarchy classHierarchy) {
		int numberOfNodesInCallGraph = callGraph.getNumberOfNodes();
		LOGGER.info("The number of nodes in the call graph: " + numberOfNodesInCallGraph);
		HashSet<CGNode> keep = new HashSet<>();
		for (CGNode node : callGraph) {
			if (Util.isStreamNode(node, classHierarchy))
				keep.add(node);
		}

		PrunedCallGraph prunedCallGraph = new PrunedCallGraph(callGraph, keep);
		int numberOfNodesInPrunedCallGraph = prunedCallGraph.getNumberOfNodes();
		LOGGER.info("The number of nodes in partial graph: " + numberOfNodesInPrunedCallGraph
				+ ". The number of saved nodes: " + (numberOfNodesInCallGraph - numberOfNodesInPrunedCallGraph));

		return prunedCallGraph;
	}
}
