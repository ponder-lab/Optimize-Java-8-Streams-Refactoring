package edu.cuny.hunter.streamrefactoring.core.analysis;

import static com.ibm.safe.typestate.core.AbstractWholeProgramSolver.DUMMY_ZERO;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.ibm.safe.typestate.base.BaseFactoid;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.core.TypeStateResult;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContext;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContextSelector;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;

import edu.cuny.hunter.streamrefactoring.core.safe.ModifiedBenignOracle;
import edu.cuny.hunter.streamrefactoring.core.safe.TypestateSolverFactory;
import edu.cuny.hunter.streamrefactoring.core.wala.CallStringWithReceivers;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

class StreamStateMachine {

	/**
	 * A list of supported terminal operation signatures.
	 */
	// @formatter:off
	private static final String[] TERMINAL_OPERATIONS = { 
			"java.util.stream.Stream.reduce",
			"java.util.stream.DoubleStream.forEach",
			"java.util.stream.DoubleStream.forEachOrdered",
			"java.util.stream.DoubleStream.toArray",
			"java.util.stream.DoubleStream.reduce", 
			"java.util.stream.DoubleStream.collect",
			"java.util.stream.DoubleStream.sum",
			"java.util.stream.DoubleStream.min",
			"java.util.stream.DoubleStream.max",
			"java.util.stream.DoubleStream.count",
			"java.util.stream.DoubleStream.average",
			"java.util.stream.DoubleStream.summaryStatistics",
			"java.util.stream.DoubleStream.anyMatch",
			"java.util.stream.DoubleStream.allMatch",
			"java.util.stream.DoubleStream.noneMatch",
			"java.util.stream.DoubleStream.findFirst",
			"java.util.stream.DoubleStream.findAny",
			"java.util.stream.IntStream.forEach",
			"java.util.stream.IntStream.forEachOrdered",
			"java.util.stream.IntStream.toArray",
			"java.util.stream.IntStream.reduce", 
			"java.util.stream.IntStream.collect",
			"java.util.stream.IntStream.sum",
			"java.util.stream.IntStream.min",
			"java.util.stream.IntStream.max",
			"java.util.stream.IntStream.count",
			"java.util.stream.IntStream.average",
			"java.util.stream.IntStream.summaryStatistics",
			"java.util.stream.IntStream.anyMatch",
			"java.util.stream.IntStream.allMatch",
			"java.util.stream.IntStream.noneMatch",
			"java.util.stream.IntStream.findFirst",
			"java.util.stream.IntStream.findAny",
			"java.util.stream.LongStream.forEach",
			"java.util.stream.LongStream.forEachOrdered",
			"java.util.stream.LongStream.toArray",
			"java.util.stream.LongStream.reduce", 
			"java.util.stream.LongStream.collect",
			"java.util.stream.LongStream.sum",
			"java.util.stream.LongStream.min",
			"java.util.stream.LongStream.max",
			"java.util.stream.LongStream.count",
			"java.util.stream.LongStream.average",
			"java.util.stream.LongStream.summaryStatistics",
			"java.util.stream.LongStream.anyMatch",
			"java.util.stream.LongStream.allMatch",
			"java.util.stream.LongStream.noneMatch",
			"java.util.stream.LongStream.findFirst",
			"java.util.stream.LongStream.findAny",
			"java.util.stream.Stream.forEach",
			"java.util.stream.Stream.forEachOrdered",
			"java.util.stream.Stream.toArray",
			"java.util.stream.Stream.reduce", 
			"java.util.stream.Stream.collect",
			"java.util.stream.Stream.min",
			"java.util.stream.Stream.max",
			"java.util.stream.Stream.count",
			"java.util.stream.Stream.anyMatch",
			"java.util.stream.Stream.allMatch",
			"java.util.stream.Stream.noneMatch",
			"java.util.stream.Stream.findFirst",
			"java.util.stream.Stream.findAny"};
	// @formatter:off

	/**
	 * A table mapping an instance and a block to the instance's possible states
	 * at that block.
	 */
	private static Table<InstanceKey, BasicBlockInContext<IExplodedBasicBlock>, Set<IDFAState>> instanceBlockToStateTable = HashBasedTable
			.create();

	private static Map<InstanceKey, Set<InstanceKey>> instanceToPredecessorMap = new HashMap<>();

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

	public void start() throws IOException, CoreException, CallGraphBuilderCancelException, CancelException,
			InvalidClassFileException, PropertiesException {
		// get the analysis engine.
		EclipseProjectAnalysisEngine<InstanceKey> engine = this.getStream().getAnalysisEngine();

		// FIXME: Do we want a different entry point?
		DefaultEntrypoint entryPoint = new DefaultEntrypoint(this.getStream().getEnclosingMethodReference(),
				this.getStream().getClassHierarchy());
		Set<Entrypoint> entryPoints = Collections.singleton(entryPoint);

		// FIXME: Do we need to build a new call graph for each entry point?
		// Doesn't make sense. Maybe we need to collect all enclosing methods
		// and use those as entry points.
		engine.buildSafeCallGraph(entryPoints);
		// TODO: Can I slice the graph so that only nodes relevant to the
		// instance in question are present?
		// TODO: Don't we already have a call graph?

		BenignOracle ora = new ModifiedBenignOracle(engine.getCallGraph(), engine.getPointerAnalysis());

		PropertiesManager manager = PropertiesManager.initFromMap(Collections.emptyMap());
		PropertiesManager.registerProperties(
				new PropertiesManager.IPropertyDescriptor[] { WholeProgramProperties.Props.LIVE_ANALYSIS });
		TypeStateOptions typeStateOptions = new TypeStateOptions(manager);
		typeStateOptions.setBooleanValue(WholeProgramProperties.Props.LIVE_ANALYSIS.getName(), false);

		TypeReference typeReference = this.getStream().getTypeReference();
		IClass streamClass = engine.getClassHierarchy().lookupClass(typeReference);
		StreamAttributeTypestateRule[] ruleArray = createStreamAttributeTypestateRules(streamClass);

		// for each rule.
		for (StreamAttributeTypestateRule rule : ruleArray) {
			// create a DFA based on the rule.
			TypeStateProperty dfa = new TypeStateProperty(rule, engine.getClassHierarchy());

			// this gets a solver that tracks all streams. TODO may need to do
			// some caching at some point here.
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

				// FIXME This doesn't make a whole lot of sense. Only looking at
				// the node of where the stream was declared: TODO: Can this be
				// somehow rewritten to get blocks corresponding to terminal
				// operations?
				Set<CGNode> cgNodes = engine.getCallGraph().getNodes(this.getStream().getEnclosingMethodReference());
				assert cgNodes.size() == 1 : "Expecting only a single CG node.";

				for (CGNode cgNode : cgNodes) {
					for (Iterator<CallSiteReference> callSites = cgNode.iterateCallSites(); callSites.hasNext();) {
						CallSiteReference callSiteReference = callSites.next();
						MethodReference calledMethod = callSiteReference.getDeclaredTarget();

						// is it a terminal operation? TODO: Should this be
						// cached somehow? Collection of all terminal operation
						// invocations?
						if (isTerminalOperation(calledMethod)) {
							// get the basic block for the call.
							ISSABasicBlock[] blocksForCall = cgNode.getIR().getBasicBlocksForCall(callSiteReference);
							assert blocksForCall.length == 1 : "Expecting only a single basic block for the call: "
									+ callSiteReference;

							for (int i = 0; i < blocksForCall.length; i++) {
								ISSABasicBlock block = blocksForCall[i];
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
									for (Iterator<SSAInstruction> it = block.iterator(); it.hasNext();) {
										SSAInstruction instruction = it.next();

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

								IntSet intSet = instanceResult.getResult().getResult(blockInContext);
								for (IntIterator it = intSet.intIterator(); it.hasNext();) {
									int nextInt = it.next();

									// retrieve the state set for this instance
									// and block.
									Set<IDFAState> stateSet = instanceBlockToStateTable.get(instanceKey,
											blockInContext);

									// if it does not yet exist.
									if (stateSet == null) {
										// allocate a new set.
										stateSet = new HashSet<>();

										// place it in the table.
										instanceBlockToStateTable.put(instanceKey, blockInContext, stateSet);
									}

									// get the facts.
									Factoid factoid = instanceResult.getDomain().getMappedObject(nextInt);
									if (factoid != DUMMY_ZERO) {
										BaseFactoid baseFactoid = (BaseFactoid) factoid;
										assert baseFactoid.instance.equals(
												instanceKey) : "Sanity check that the fact instance should be the same as the instance being examined.";

										// add the encountered state to the set.
										stateSet.add(baseFactoid.state);
									}
								}
							}
						}
					}
				}
			}

			// fill the instance to predecessor map.
			for (Iterator<InstanceKey> it = result.iterateInstances(); it.hasNext();) {
				InstanceKey instance = it.next();
				NormalAllocationInNode allocationInNode = (NormalAllocationInNode) instance;
				CGNode node = allocationInNode.getNode();
				CallStringContext context = (CallStringContext) node.getContext();
				CallStringWithReceivers callString = (CallStringWithReceivers) context
						.get(CallStringContextSelector.CALL_STRING);
				instanceToPredecessorMap.merge(instance, callString.getPossibleReceivers(), (x, y) -> {
					x.addAll(y);
					return x;
				});
			}

			// TODO: This should be cached for all class instances.
			Map<InstanceKey, Collection<IDFAState>> originStreamToMergedTypeStateMap = new HashMap<>();

			// for each terminal operation call, I think?
			for (BasicBlockInContext<IExplodedBasicBlock> block : terminalBlockToPossibleReceivers.keySet()) {
				OrdinalSet<InstanceKey> possibleReceivers = terminalBlockToPossibleReceivers.get(block);
				// for each possible receiver of the terminal operation call.
				for (InstanceKey instanceKey : possibleReceivers) {
					Set<IDFAState> possibleStates = computeMergedTypeState(instanceKey, block, rule);
					Set<InstanceKey> possibleOriginStreams = computePossibleOriginStreams(instanceKey);
					possibleOriginStreams.forEach(
							os -> originStreamToMergedTypeStateMap.merge(os, new HashSet<>(possibleStates), (x, y) -> {
								x.addAll(y);
								return x;
							}));
				}
			}

			// TODO: Also need to cache this.
			InstanceKey streamInQuestionInstanceKey = this.getStream().getInstanceKey(instanceToPredecessorMap.keySet(),
					engine.getCallGraph());
			Collection<IDFAState> states = originStreamToMergedTypeStateMap.get(streamInQuestionInstanceKey);
			// Map IDFAState to StreamExecutionMode, etc., and add them to the
			// possible stream states but only if they're not bottom (for those,
			// we fall back to the initial state).
			rule.addPossibleAttributes(this.getStream(), states);
		}

		System.out.println("Execution modes: " + this.getStream().getPossibleExecutionModes());
		System.out.println("Orderings: " + this.getStream().getPossibleOrderings());
	}

	/**
	 * The typestate rules to use.
	 */
	protected static StreamAttributeTypestateRule[] createStreamAttributeTypestateRules(IClass streamClass) {
		// @formatter:off
		return new StreamAttributeTypestateRule[] {
				new StreamExecutionModeTypeStateRule(streamClass),
				new StreamOrderingTypeStateRule(streamClass)};
		// @formatter:on
	}

	// FIXME: The performance of this method is not good. We should build a map
	// between block numbers and the corresponding basicBlockInContext. But,
	// when do we populate the map? In other words, we need a map of block
	// numbers to BasicBlockInContexts whose delegate's original block number
	// matches the key for a given supergraph. Should it be a table? Or, maybe
	// it's just a collection of maps, one for each supergraph? ICFGSupergraph
	// -> Map. Then, Integer -> BasicBlockInContext. But, now must also consider
	// the cgNode. #21.
	/**
	 * Return the basic block in context for the given block in the procedure
	 * represented by the given call graph node in the given supergraph.
	 * 
	 * @param block
	 *            The block in which to find the corresponding block in context
	 *            in the supergraph.
	 * @param cgNode
	 *            The call graph node representing the procedure that contains
	 *            the block.
	 * @param supergraph
	 *            The supergraph in which to look up the corresponding block in
	 *            context.
	 * @return The block in context in the given supergraph that corresponds to
	 *         the given block with the procedure represented by the given call
	 *         graph node.
	 */
	private static Optional<BasicBlockInContext<IExplodedBasicBlock>> getBasicBlockInContextForBlock(
			ISSABasicBlock block, CGNode cgNode, ICFGSupergraph supergraph) {
		// can we search the supergraph for the corresponding block? Do I need
		// to search the entire graph?
		// TODO: For #20, this will probably need to change.
		for (BasicBlockInContext<IExplodedBasicBlock> basicBlockInContext : supergraph) {
			CGNode blockInContextProcedure = supergraph.getProcOf(basicBlockInContext);
			if (blockInContextProcedure == cgNode) {
				IExplodedBasicBlock delegate = basicBlockInContext.getDelegate();
				if (!delegate.isEntryBlock() && !delegate.isExitBlock()
						&& delegate.getOriginalNumber() == block.getNumber())
					return Optional.of(basicBlockInContext);
			}
		}
		return Optional.empty();
	}

	// TODO: This should probably be cached.
	private static Set<InstanceKey> computePossibleOriginStreams(InstanceKey instanceKey) {
		// if there is no instance.
		if (instanceKey == null)
			// there are no origins.
			return Collections.emptySet();

		// otherwise, retrieve the predecessors of the instance.
		Set<InstanceKey> predecessors = instanceToPredecessorMap.get(instanceKey);

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

	private static Set<IDFAState> computeMergedTypeState(InstanceKey instanceKey,
			BasicBlockInContext<IExplodedBasicBlock> block, StreamAttributeTypestateRule rule) {
		Set<InstanceKey> predecessors = instanceToPredecessorMap.get(instanceKey);
		Set<IDFAState> possibleInstanceStates = instanceBlockToStateTable.get(instanceKey, block);

		if (predecessors.isEmpty())
			return possibleInstanceStates;

		Set<IDFAState> ret = new HashSet<>();
		for (InstanceKey pred : predecessors) {
			ret.addAll(mergeTypeStates(possibleInstanceStates, computeMergedTypeState(pred, block, rule), rule));
		}

		return ret;
	}

	private static Collection<? extends IDFAState> mergeTypeStates(Set<IDFAState> set1, Set<IDFAState> set2,
			StreamAttributeTypestateRule rule) {
		if (set1.isEmpty())
			return set2;
		else if (set2.isEmpty())
			return set1;

		Set<IDFAState> ret = new HashSet<>();

		for (IDFAState state1 : set1) {
			for (IDFAState state2 : set2) {
				ret.add(selectState(state1, state2, rule));
			}
		}

		return ret;
	}

	private static IDFAState selectState(IDFAState state1, IDFAState state2, StreamAttributeTypestateRule rule) {
		if (state1 == rule.getBottomState())
			return state2;
		else
			return state1;
	}

	private static boolean isTerminalOperation(MethodReference calledMethod) {
		String signature = calledMethod.getSignature();
		return Arrays.stream(TERMINAL_OPERATIONS).anyMatch(to -> signature.startsWith(to));
	}

	protected Stream getStream() {
		return stream;
	}

	public static void clearCaches() {
		instanceBlockToStateTable.clear();
		instanceToPredecessorMap.clear();
	}
}