package edu.cuny.hunter.streamrefactoring.core.analysis;

import static com.ibm.safe.typestate.core.AbstractWholeProgramSolver.DUMMY_ZERO;
import static edu.cuny.hunter.streamrefactoring.core.analysis.StreamAttributeTypestateRule.BOTTOM_STATE_NAME;

import java.io.IOException;
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
import org.eclipse.core.runtime.NullProgressMonitor;

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
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.propagation.InstanceFieldPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
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
import edu.cuny.hunter.streamrefactoring.core.safe.TypestateSolverFactory;
import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;
import edu.cuny.hunter.streamrefactoring.core.wala.CallStringWithReceivers;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

class StreamStateMachine {

	/**
	 * A table mapping an instance and a block to the instance's possible states at
	 * that block.
	 */
	private static Table<InstanceKey, BasicBlockInContext<IExplodedBasicBlock>, Map<TypestateRule, Set<IDFAState>>> instanceBlockStateTable = HashBasedTable
			.create();

	/**
	 * A set of instances whose reduce ordering may matter.
	 */
	private static Set<InstanceKey> instancesWhoseReduceOrderingPossiblyMatters = new HashSet<>();

	/**
	 * A set of instances whose pipelines contain behavioral parameters that may
	 * have side-effects.
	 */
	private static Set<InstanceKey> instancesWithSideEffects = new HashSet<>();

	/**
	 * All of the stream's predecessors.
	 */
	private static Map<InstanceKey, Set<InstanceKey>> instanceToAllPredecessorsMap = new HashMap<>();

	/**
	 * A stream's immediate predecessor.
	 */
	private static Map<InstanceKey, Set<InstanceKey>> instanceToPredecessorsMap = new HashMap<>();

	/**
	 * Instances whose pipelines may contain stateful intermediate operations.
	 */
	private static Map<InstanceKey, Boolean> instanceToStatefulIntermediateOperationContainment = new HashMap<>();

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	private static Map<InstanceKey, Map<TypestateRule, Set<IDFAState>>> originStreamToMergedTypeStateMap = new HashMap<>();

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

	public static void clearCaches() {
		instanceBlockStateTable.clear();
		instanceToPredecessorsMap.clear();
		instanceToAllPredecessorsMap.clear();
		originStreamToMergedTypeStateMap.clear();
		instancesWithSideEffects.clear();
		instanceToStatefulIntermediateOperationContainment.clear();
		instancesWhoseReduceOrderingPossiblyMatters.clear();
	}

	private static Set<IDFAState> computeMergedTypeState(InstanceKey instanceKey,
			BasicBlockInContext<IExplodedBasicBlock> block, StreamAttributeTypestateRule rule) {
		Set<InstanceKey> predecessors = instanceToPredecessorsMap.get(instanceKey);
		Map<TypestateRule, Set<IDFAState>> ruleToStates = instanceBlockStateTable.get(instanceKey, block);

		if (ruleToStates == null)
			return Collections.emptySet();

		Set<IDFAState> possibleInstanceStates = ruleToStates.get(rule);

		if (predecessors.isEmpty())
			return possibleInstanceStates;

		Set<IDFAState> ret = new HashSet<>();
		for (InstanceKey pred : predecessors) {
			ret.addAll(mergeTypeStates(possibleInstanceStates, computeMergedTypeState(pred, block, rule)));
		}

		return ret;
	}

	// TODO: This should probably be cached.
	private static Set<InstanceKey> computePossibleOriginStreams(InstanceKey instanceKey) {
		// if there is no instance.
		if (instanceKey == null)
			// there are no origins.
			return Collections.emptySet();

		// otherwise, retrieve the predecessors of the instance.
		Set<InstanceKey> predecessors = instanceToPredecessorsMap.get(instanceKey);

		// if there are no predecessors for this instance.
		if (predecessors.isEmpty())
			// then this instance must be its own origin.
			return Collections.singleton(instanceKey);

		// otherwise, we have a situation where the instance in question has one
		// or more predecessors.
		// In this case, the possible origins of the given instance are the
		// possible origins of each of its predecessors.
		return predecessors.stream().map(StreamStateMachine::computePossibleOriginStreams).flatMap(os -> os.stream())
				.collect(Collectors.toSet());
	}

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

	private static boolean deriveRomForVoidMethod(SSAInvokeInstruction invokeInstruction) {
		MethodReference declaredTarget = invokeInstruction.getCallSite().getDeclaredTarget();

		if (isTerminalOperationWhereReduceOrderMatters(declaredTarget))
			return true;
		else if (isTerminalOperationWhereReduceOrderDoesNotMatter(declaredTarget))
			return false;
		else
			throw new IllegalStateException("Can't decipher ROM for method: " + declaredTarget);
	}

	private static void discoverLambdaSideEffects(EclipseProjectAnalysisEngine<InstanceKey> engine,
			Map<CGNode, OrdinalSet<PointerKey>> mod, Iterable<InstanceKey> instances,
			MethodReference declaredTargetOfCaller, IR ir, int use) {
		// look up it's definition.
		DefUse defUse = engine.getCache().getDefUse(ir);
		// it should be a call.
		SSAInstruction def = defUse.getDef(use);

		// if we found it.
		if (def != null) {
			if (def instanceof SSAAbstractInvokeInstruction) {
				SSAAbstractInvokeInstruction instruction = (SSAAbstractInvokeInstruction) def;

				// take a look at the nodes in the caller.
				Set<CGNode> nodes = engine.getCallGraph().getNodes(declaredTargetOfCaller);

				// for each caller node.
				for (CGNode cgNode : nodes) {
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

								for (PointerKey pointerKey : modSet) {
									if (!filterPointerKey(pointerKey, engine))
										filteredModSet.add(pointerKey);
								}

								LOGGER.fine(() -> "#filtered modified locations: " + filteredModSet.size());

								// if it's non-empty.
								if (!filteredModSet.isEmpty()) {
									filteredModSet
											.forEach(pk -> LOGGER.fine(() -> "Filtered modified location: " + pk));

									// mark the instances whose pipeline may
									// have side-effects.
									instances.forEach(instancesWithSideEffects::add);
								}
							}
							// we found a match between the graph call site
							// and the one in the definition. No need to
							// continue.
							break;
						}
					}
				}
			} else
				LOGGER.warning("Def was an instance of a: " + def.getClass());
		}
	}

	private static void discoverPossibleStatefulIntermediateOperations(AggregateSolverResult result,
			IClassHierarchy hierarchy, CallGraph callGraph) throws IOException, CoreException {
		// for each instance in the analysis result (these should be the
		// "intermediate" streams).
		for (Iterator<InstanceKey> it = result.iterateInstances(); it.hasNext();) {
			InstanceKey instance = it.next();

			if (!instanceToStatefulIntermediateOperationContainment.containsKey(instance)) {
				// make sure that the stream is the result of an intermediate
				// operation.
				if (!isStreamCreatedFromIntermediateOperation(instance, hierarchy, callGraph))
					continue;

				CallStringWithReceivers callString = Util.getCallString(instance);

				boolean found = false;
				for (CallSiteReference callSiteReference : callString.getCallSiteRefs()) {
					if (isStatefulIntermediateOperation(callSiteReference.getDeclaredTarget())) {
						found = true; // found one.
						break; // no need to continue checking.
					}
				}
				instanceToStatefulIntermediateOperationContainment.put(instance, found);
			}
		}
	}

	/**
	 * Returns true if the given {@link PointerKey} should be filtered from the
	 * {@link ModRef} analysis.
	 *
	 * @param pointerKey
	 *            The {@link PointerKey} in question.
	 * @param engine
	 *            The {@link AnalysisEngine} to use.
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
				boolean fromStreamPackage = packageAtom.startsWith(Atom.findOrCreateUnicodeAtom("java/util/stream"));

				if (ret == null) {
					// haven't decided yet. Initialize.
					ret = fromStreamPackage;
				} else if (ret != fromStreamPackage) {
					// we have a discrepancy.
					throw new IllegalArgumentException("Can't determine consistent write location package");
				}
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

			TypeReference returnType = calledMethod.getReturnType();
			LOGGER.fine(() -> "Return type is: " + returnType);

			boolean implementsBaseStream = Util.implementsBaseStream(returnType, hierarchy);
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
					for (InstanceKey receiver : possibleReceivers) {
						if (Util.implementsBaseStream(receiver.getConcreteType().getReference(), hierarchy))
							ret.add(receiver);
					}
				}
			}
		}
		return ret;
	}

	private static Set<InstanceKey> getAllPredecessors(InstanceKey instanceKey) {
		if (!instanceToAllPredecessorsMap.containsKey(instanceKey)) {
			Set<InstanceKey> ret = new HashSet<>();

			// add the instance's predecessors.
			ret.addAll(instanceToPredecessorsMap.get(instanceKey));

			// add their predecessors.
			ret.addAll(instanceToPredecessorsMap.get(instanceKey).stream()
					.flatMap(ik -> getAllPredecessors(ik).stream()).collect(Collectors.toSet()));

			instanceToAllPredecessorsMap.put(instanceKey, ret);
			return ret;
		} else
			return instanceToAllPredecessorsMap.get(instanceKey);
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
	 * @param block
	 *            The block in which to find the corresponding block in context in
	 *            the supergraph.
	 * @param cgNode
	 *            The call graph node representing the procedure that contains the
	 *            block.
	 * @param supergraph
	 *            The supergraph in which to look up the corresponding block in
	 *            context.
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

	private static boolean isScalar(Collection<TypeAbstraction> types) {
		Boolean ret = null;

		for (TypeAbstraction typeAbstraction : types) {
			boolean scalar = isScalar(typeAbstraction);

			if (ret == null)
				ret = scalar;
			else if (ret != scalar)
				throw new IllegalArgumentException("Inconsistent types: " + types);
		}

		return ret;
	}

	private static boolean isScalar(TypeAbstraction typeAbstraction) {
		TypeReference typeReference = typeAbstraction.getTypeReference();

		if (typeReference.isArrayType())
			return false;
		else if (typeReference.equals(TypeReference.Void))
			throw new IllegalArgumentException("Void is neither scalar or nonscalar.");
		else if (typeReference.isPrimitiveType())
			return true;
		else if (typeReference.isReferenceType()) {
			IClass type = typeAbstraction.getType();
			return !Util.isIterable(type) && type.getAllImplementedInterfaces().stream().noneMatch(Util::isIterable);
		} else
			throw new IllegalArgumentException("Can't tell if type is scalar: " + typeAbstraction);
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
			return Util.isBaseStream(type) || type.getAllImplementedInterfaces().stream().anyMatch(Util::isBaseStream);
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

		for (IDFAState state1 : set1) {
			for (IDFAState state2 : set2) {
				ret.add(selectState(state1, state2));
			}
		}

		return ret;
	}

	private static void propagateStreamInstanceProperty(Collection<InstanceKey> streamInstancesWithProperty) {
		streamInstancesWithProperty.addAll(streamInstancesWithProperty.stream()
				.flatMap(ik -> getAllPredecessors(ik).stream()).collect(Collectors.toSet()));
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
	 * The stream that this state machine represents.
	 */
	private final Stream stream;

	/**
	 * Constructs a new {@link StreamStateMachine} given a {@link Stream} to
	 * represent.
	 *
	 * @param stream
	 *            The representing stream.
	 */
	StreamStateMachine(Stream stream) {
		this.stream = stream;
	}

	private boolean deriveRomForNonScalarMethod(Collection<TypeAbstraction> possibleReturnTypes)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		Ordering ordering;
		try {
			ordering = this.getStream().getOrderingInference().inferOrdering(possibleReturnTypes);
		} catch (InconsistentPossibleOrderingException e) {
			// default to ordered #55.
			ordering = Ordering.ORDERED;
			LOGGER.log(Level.WARNING, "Inconsistently ordered possible return types encountered: " + possibleReturnTypes
					+ ". Defaulting to: " + ordering, e);
		}

		LOGGER.info("Ordering of reduction type is: " + ordering);

		switch (ordering) {
		case UNORDERED:
			return false;
		case ORDERED:
			return true;
		default:
			throw new IllegalStateException("Logic missing ordering.");
		}
	}

	private void discoverIfReduceOrderingPossiblyMatters(
			Map<BasicBlockInContext<IExplodedBasicBlock>, OrdinalSet<InstanceKey>> terminalBlockToPossibleReceivers)
			throws IOException, CoreException, UnknownIfReduceOrderMattersException, NoniterableException,
			NoninstantiableException, CannotExtractSpliteratorException {
		// for each terminal operation call, I think?
		for (BasicBlockInContext<IExplodedBasicBlock> block : terminalBlockToPossibleReceivers.keySet()) {
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

					possibleReturnTypes = Util.getPossibleTypesInterprocedurally(block.getNode(), returnValue,
							this.getStream().getAnalysisEngine().getHeapGraph().getHeapModel(),
							this.getStream().getAnalysisEngine().getPointerAnalysis(), this.getStream());

					LOGGER.fine("Possible reduce types are: " + possibleReturnTypes);
				} else {
					// it's a void method.
					possibleReturnTypes = Collections.singleton(JavaPrimitiveType.VOID);
				}

				boolean rom = false;

				if (isVoid(possibleReturnTypes))
					rom = deriveRomForVoidMethod(invokeInstruction);
				else {
					boolean scalar = isScalar(possibleReturnTypes);
					if (scalar)
						rom = deriveRomForScalarMethod(invokeInstruction);
					else if (!scalar)
						rom = deriveRomForNonScalarMethod(possibleReturnTypes);
					else
						throw new IllegalStateException(
								"Can't derive ROM for possible return types: " + possibleReturnTypes);
				}

				// if reduce ordering matters.
				if (rom) {
					LOGGER.fine(() -> "Reduce ordering matters for: " + invokeInstruction);
					OrdinalSet<InstanceKey> possibleReceivers = terminalBlockToPossibleReceivers.get(block);
					possibleReceivers.forEach(instancesWhoseReduceOrderingPossiblyMatters::add);
				} else
					// otherwise, just log.
					LOGGER.fine(() -> "Reduce ordering doesn't matter for: " + invokeInstruction);

				++processedInstructions;
			}
			assert processedInstructions == 1 : "Expecting to process one and only one instruction here.";
		}
	}

	private void discoverPossibleSideEffects(AggregateSolverResult result,
			Map<BasicBlockInContext<IExplodedBasicBlock>, OrdinalSet<InstanceKey>> terminalBlockToPossibleReceivers)
			throws IOException, CoreException {
		EclipseProjectAnalysisEngine<InstanceKey> engine = this.getStream().getAnalysisEngine();

		// create the ModRef analysis.
		ModRef<InstanceKey> modRef = ModRef.make();

		// compute modifications over the call graph.
		// TODO: Should this be cached? Didn't have luck caching the call graph.
		// Perhaps this will be similar.
		Map<CGNode, OrdinalSet<PointerKey>> mod = modRef.computeMod(engine.getCallGraph(), engine.getPointerAnalysis());

		// for each terminal operation call, I think?
		for (BasicBlockInContext<IExplodedBasicBlock> block : terminalBlockToPossibleReceivers.keySet()) {
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

					discoverLambdaSideEffects(engine, mod, terminalBlockToPossibleReceivers.get(block), declaredTarget,
							ir, paramUse);
				}
				++processedInstructions;
			}

			assert processedInstructions == 1 : "Expecting to process one and only one instruction here.";
		}

		// for each instance in the analysis result (these should be the
		// "intermediate" streams).
		for (Iterator<InstanceKey> it = result.iterateInstances(); it.hasNext();) {
			InstanceKey instance = it.next();

			// make sure that the stream is the result of an intermediate
			// operation.
			if (!isStreamCreatedFromIntermediateOperation(instance, this.getStream().getClassHierarchy(),
					engine.getCallGraph()))
				continue;

			CallStringWithReceivers callString = Util.getCallString(instance);
			CallSiteReference[] callSiteRefs = callString.getCallSiteRefs();
			assert callSiteRefs.length == 2 : "Expecting call sites two-deep.";

			// get the target of the caller.
			MethodReference callerDeclaredTarget = callSiteRefs[1].getDeclaredTarget();

			// get it's IR.
			IMethod callerTargetMethod = engine.getClassHierarchy().resolveMethod(callerDeclaredTarget);
			boolean fallback = false;

			if (callerTargetMethod == null) {
				LOGGER.warning("Cannot resolve caller declared target method: " + callerDeclaredTarget);

				// fall back.
				callerTargetMethod = callString.getMethods()[1];
				LOGGER.warning("Falling back to method: " + callerTargetMethod);
				fallback = true;
			}

			IR ir = engine.getCache().getIR(callerTargetMethod);

			if (ir == null) {
				LOGGER.warning("Can't find IR for target: " + callerTargetMethod);
				continue; // next instance.
			}

			// get calls to the caller target.
			// if we are falling back, use index 1, otherwise stick with index
			// 0.
			int callSiteRefsInx = fallback ? 1 : 0;
			SSAAbstractInvokeInstruction[] calls = ir.getCalls(callSiteRefs[callSiteRefsInx]);
			assert calls.length == 1 : "Are we only expecting one call here?";

			// I guess we're only interested in ones with a single behavioral
			// parameter (the first parameter is implicit).
			if (calls[0].getNumberOfUses() == 2) {
				// get the use of the first parameter.
				int use = calls[0].getUse(1);
				discoverLambdaSideEffects(engine, mod, Collections.singleton(instance), callerDeclaredTarget, ir, use);
			}
		}
	}

	private void discoverTerminalOperations(AggregateSolverResult result,
			Map<BasicBlockInContext<IExplodedBasicBlock>, OrdinalSet<InstanceKey>> terminalBlockToPossibleReceivers,
			InstanceKey streamInstanceInQuestion) throws RequireTerminalOperationException {
		Collection<OrdinalSet<InstanceKey>> receiverSetsThatHaveTerminalOperations = terminalBlockToPossibleReceivers
				.values();

		// This will be the OK set.
		Collection<InstanceKey> validStreams = new HashSet<InstanceKey>();

		// Now, we need to flatten the receiver sets.
		for (OrdinalSet<InstanceKey> receiverSet : receiverSetsThatHaveTerminalOperations) {
			// for each receiver set
			for (InstanceKey instance : receiverSet) {
				// add it to the OK set.
				validStreams.add(instance);
			}
		}

		// Now, we have the OK set. Let's propagate it.
		propagateStreamInstanceProperty(validStreams);

		// Now, we will find the set containing all stream instances.
		Set<InstanceKey> allStreamInstances = new HashSet<InstanceKey>();

		// for each instance in the typestate analysis result.
		for (Iterator<InstanceKey> it = result.iterateInstances(); it.hasNext();) {
			// add the current instance to the set.
			allStreamInstances.add(it.next());
		}

		// Now, we have the set of all stream instances.

		// Let's now find the bad set.
		allStreamInstances.removeAll(validStreams);
		Set<InstanceKey> badStreamInstances = allStreamInstances;

		// if the stream in question is "bad".
		if (badStreamInstances.contains(streamInstanceInQuestion))
			throw new RequireTerminalOperationException("Require terminal operations.");
	}

	protected Stream getStream() {
		return stream;
	}

	public void start() throws IOException, CoreException, CallGraphBuilderCancelException, CancelException,
			InvalidClassFileException, PropertiesException, UnknownIfReduceOrderMattersException, NoniterableException,
			NoninstantiableException, CannotExtractSpliteratorException, RequireTerminalOperationException,
			InstanceKeyNotFoundException {
		// get the analysis engine.
		EclipseProjectAnalysisEngine<InstanceKey> engine = this.getStream().getAnalysisEngine();
		BenignOracle ora = new ModifiedBenignOracle(engine.getCallGraph(), engine.getPointerAnalysis());

		PropertiesManager manager = PropertiesManager.initFromMap(Collections.emptyMap());
		PropertiesManager.registerProperties(
				new PropertiesManager.IPropertyDescriptor[] { WholeProgramProperties.Props.LIVE_ANALYSIS });
		TypeStateOptions typeStateOptions = new TypeStateOptions(manager);
		typeStateOptions.setBooleanValue(WholeProgramProperties.Props.LIVE_ANALYSIS.getName(), false);
		// TODO: #127 should also set entry points.

		TypeReference typeReference = this.getStream().getTypeReference();
		IClass streamClass = engine.getClassHierarchy().lookupClass(typeReference);
		StreamAttributeTypestateRule[] ruleArray = createStreamAttributeTypestateRules(streamClass);

		// for each rule.
		for (StreamAttributeTypestateRule rule : ruleArray) {
			// create a DFA based on the rule.
			TypeStateProperty dfa = new TypeStateProperty(rule, engine.getClassHierarchy());

			// this gets a solver that tracks all streams. TODO may need to do
			// some caching at some point here. NOTE: Seems to be more difficult
			// than initially imagined.
			LOGGER.info(() -> "Starting solver for stream: " + this.getStream());
			ISafeSolver solver = TypestateSolverFactory.getSolver(engine.getOptions(), engine.getCallGraph(),
					engine.getPointerAnalysis(), engine.getHeapGraph(), dfa, ora, typeStateOptions, null, null, null);

			AggregateSolverResult result;
			try {
				result = (AggregateSolverResult) solver.perform(new NullProgressMonitor());
			} catch (SolverTimeoutException | MaxFindingsException | SetUpException | WalaException e) {
				throw new RuntimeException("Exception caught during typestate analysis.", e);
			}

			Map<BasicBlockInContext<IExplodedBasicBlock>, OrdinalSet<InstanceKey>> terminalBlockToPossibleReceivers = new HashMap<>();

			// for each instance in the typestate analysis result.
			for (Iterator<InstanceKey> iterator = result.iterateInstances(); iterator.hasNext();) {
				// get the instance's key.
				InstanceKey instanceKey = iterator.next();

				// get the result for that instance.
				TypeStateResult instanceResult = (TypeStateResult) result.getInstanceResult(instanceKey);

				// get the supergraph for the instance result.
				ICFGSupergraph supergraph = instanceResult.getSupergraph();

				// TODO: Can this be somehow rewritten to get blocks
				// corresponding to terminal operations?
				// for each call graph node in the call graph.
				for (CGNode cgNode : engine.getCallGraph()) {
					// separating client from library code
					// improve performance for #103
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

								ISSABasicBlock[] blocksForCall = cgNode.getIR()
										.getBasicBlocksForCall(callSiteReference);

								assert blocksForCall.length == 1 : "Expecting only a single basic block for the call: "
										+ callSiteReference;

								for (ISSABasicBlock block : blocksForCall) {
									BasicBlockInContext<IExplodedBasicBlock> blockInContext = getBasicBlockInContextForBlock(
											block, cgNode, supergraph)
													.orElseThrow(() -> new IllegalStateException(
															"No basic block in context for block: " + block));

									if (!terminalBlockToPossibleReceivers.containsKey(blockInContext)) {
										// associate possible receivers with the
										// blockInContext.
										// search through each instruction in the
										// block.
										int processedInstructions = 0;
										for (SSAInstruction instruction : block) {
											// if it's a phi instruction.
											if (instruction instanceof SSAPhiInstruction)
												// skip it. The pointer analysis
												// below will handle it.
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

											OrdinalSet<InstanceKey> previousReceivers = terminalBlockToPossibleReceivers
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
										Map<TypestateRule, Set<IDFAState>> ruleToStates = instanceBlockStateTable
												.get(instanceKey, blockInContext);

										// if it doesn't yet exist.
										if (ruleToStates == null) {
											// allocate a new rule map.
											ruleToStates = new HashMap<>();

											// place it in the table.
											instanceBlockStateTable.put(instanceKey, blockInContext, ruleToStates);
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
				}
			}

			// fill the instance side-effect set.
			// FIXME: I don't think that this belongs in the typestate rule
			// loop.
			discoverPossibleSideEffects(result, terminalBlockToPossibleReceivers);

			// discover whether any stateful intermediate operations are
			// present.
			discoverPossibleStatefulIntermediateOperations(result, this.getStream().getClassHierarchy(),
					this.getStream().getAnalysisEngine().getCallGraph());

			// does reduction order matter?
			discoverIfReduceOrderingPossiblyMatters(terminalBlockToPossibleReceivers);

			// fill the instance to predecessors map.
			for (Iterator<InstanceKey> it = result.iterateInstances(); it.hasNext();) {
				InstanceKey instance = it.next();
				CallStringWithReceivers callString = Util.getCallString(instance);
				Set<InstanceKey> possibleReceivers = new HashSet<>(callString.getPossibleReceivers());

				// get any additional receivers if necessary #36.
				Collection<? extends InstanceKey> additionalNecessaryReceiversFromPredecessors = getAdditionalNecessaryReceiversFromPredecessors(
						instance, this.getStream().getClassHierarchy(),
						this.getStream().getAnalysisEngine().getCallGraph());
				LOGGER.fine(() -> "Adding additional receivers: " + additionalNecessaryReceiversFromPredecessors);
				possibleReceivers.addAll(additionalNecessaryReceiversFromPredecessors);

				instanceToPredecessorsMap.merge(instance, possibleReceivers, (x, y) -> {
					x.addAll(y);
					return x;
				});
			}

			// for each terminal operation call, I think?
			for (BasicBlockInContext<IExplodedBasicBlock> block : terminalBlockToPossibleReceivers.keySet()) {
				OrdinalSet<InstanceKey> possibleReceivers = terminalBlockToPossibleReceivers.get(block);
				// for each possible receiver of the terminal operation call.
				// FIXME why mess with all blocks here? Why not just those with
				// receivers related to the stream in question?
				for (InstanceKey instanceKey : possibleReceivers) {
					Set<IDFAState> possibleStates = computeMergedTypeState(instanceKey, block, rule);
					Set<InstanceKey> possibleOriginStreams = computePossibleOriginStreams(instanceKey);
					possibleOriginStreams.forEach(os -> {
						// create a new map.
						Map<TypestateRule, Set<IDFAState>> ruleToStates = new HashMap<>();
						ruleToStates.put(rule, new HashSet<>(possibleStates));

						// merge it.
						originStreamToMergedTypeStateMap.merge(os, ruleToStates, (m1, m2) -> {
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

			InstanceKey streamInQuestionInstanceKey = this.getStream()
					.getInstanceKey(instanceToPredecessorsMap.keySet(), engine.getCallGraph());

			discoverTerminalOperations(result, terminalBlockToPossibleReceivers, streamInQuestionInstanceKey);

			Collection<IDFAState> states = originStreamToMergedTypeStateMap.get(streamInQuestionInstanceKey).get(rule);
			// Map IDFAState to StreamExecutionMode, etc., and add them to the
			// possible stream states but only if they're not bottom (for those,
			// we fall back to the initial state).
			rule.addPossibleAttributes(this.getStream(), states);
		}

		InstanceKey streamInstanceKey = this.getStream().getInstanceKey(instanceToPredecessorsMap.keySet(),
				engine.getCallGraph());

		// propagate the instances with side-effects.
		propagateStreamInstanceProperty(instancesWithSideEffects);

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

		instanceToStatefulIntermediateOperationContainment.entrySet().stream().filter(Entry::getValue)
				.map(Entry::getKey).flatMap(ik -> getAllPredecessors(ik).stream())
				.collect(Collectors.toMap(Function.identity(), v -> true, remappingFunction))
				.forEach((k, v) -> instanceToStatefulIntermediateOperationContainment.merge(k, v, remappingFunction));

		// propagate the instances whose reduce ordering possibly matters.
		propagateStreamInstanceProperty(instancesWhoseReduceOrderingPossiblyMatters);

		// determine if this stream has possible side-effects.
		this.getStream().setHasPossibleSideEffects(instancesWithSideEffects.contains(streamInstanceKey));

		// determine if this stream has possible stateful intermediate
		// operations.
		this.getStream().setHasPossibleStatefulIntermediateOperations(
				instanceToStatefulIntermediateOperationContainment.getOrDefault(streamInstanceKey, false));

		// determine if this stream reduce ordering possibly matters
		this.getStream().setReduceOrderingPossiblyMatters(
				instancesWhoseReduceOrderingPossiblyMatters.contains(streamInstanceKey));
	}
}
