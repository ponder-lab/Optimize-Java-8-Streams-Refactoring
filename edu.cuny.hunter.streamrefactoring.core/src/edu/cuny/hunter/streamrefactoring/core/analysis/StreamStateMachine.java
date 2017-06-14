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
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
import com.ibm.safe.typestate.base.BaseFactoid;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.core.TypeStateResult;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
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
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.strings.Atom;

import edu.cuny.hunter.streamrefactoring.core.safe.ModifiedBenignOracle;
import edu.cuny.hunter.streamrefactoring.core.safe.TypestateSolverFactory;
import edu.cuny.hunter.streamrefactoring.core.wala.CallStringWithReceivers;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

class StreamStateMachine {

	/**
	 * A list of stateful intermediate operation signatures.
	 */
	// @formatter:off
	private static final String[] STATEFUL_INTERMEDIATE_OPERATIONS = { 
			"java.util.stream.Stream.distinct",
			"java.util.stream.Stream.sorted", 
			"java.util.stream.Stream.limit", 
			"java.util.stream.Stream.skip",
			"java.util.stream.DoubleStream.distinct", 
			"java.util.stream.DoubleStream.sorted",
			"java.util.stream.DoubleStream.limit", 
			"java.util.stream.DoubleStream.skip",
			"java.util.stream.IntStream.distinct", 
			"java.util.stream.IntStream.sorted",
			"java.util.stream.IntStream.limit", 
			"java.util.stream.IntStream.skip",
			"java.util.stream.LongStream.distinct", 
			"java.util.stream.LongStream.sorted",
			"java.util.stream.LongStream.limit", 
			"java.util.stream.LongStream.skip" };
	// @formatter:on

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
	// @formatter:on

	/**
	 * A table mapping an instance and a block to the instance's possible states
	 * at that block.
	 */
	private static Table<InstanceKey, BasicBlockInContext<IExplodedBasicBlock>, Set<IDFAState>> instanceBlockStateTable = HashBasedTable
			.create();

	/**
	 * A stream's immediate predecessor.
	 */
	private static Map<InstanceKey, Set<InstanceKey>> instanceToPredecessorsMap = new HashMap<>();

	/**
	 * All of the stream's predecessors.
	 */
	private static Map<InstanceKey, Set<InstanceKey>> instanceToAllPredecessorsMap = new HashMap<>();

	private static Map<InstanceKey, Collection<IDFAState>> originStreamToMergedTypeStateMap = new HashMap<>();

	/**
	 * A set of instances whose pipelines contain behavioral parameters that may
	 * have side-effects.
	 */
	private static Set<InstanceKey> instancesWithSideEffects = new HashSet<>();

	/**
	 * Instances whose pipelines may contain stateful intermediate operations.
	 */
	private static Map<InstanceKey, Boolean> instanceToStatefulIntermediateOperationContainment = new HashMap<>();

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
									Set<IDFAState> stateSet = instanceBlockStateTable.get(instanceKey, blockInContext);

									// if it does not yet exist.
									if (stateSet == null) {
										// allocate a new set.
										stateSet = new HashSet<>();

										// place it in the table.
										instanceBlockStateTable.put(instanceKey, blockInContext, stateSet);
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

			// fill the instance side-effect set.
			discoverPossibleSideEffects(result, terminalBlockToPossibleReceivers);

			// discover whether any stateful intermediate operations are
			// present.
			discoverPossibleStatefulIntermediateOperations(result);

			// fill the instance to predecessors map.
			for (Iterator<InstanceKey> it = result.iterateInstances(); it.hasNext();) {
				InstanceKey instance = it.next();
				CallStringWithReceivers callString = getCallString(instance);

				instanceToPredecessorsMap.merge(instance, callString.getPossibleReceivers(), (x, y) -> {
					x.addAll(y);
					return x;
				});
			}

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

			InstanceKey streamInQuestionInstanceKey = this.getStream()
					.getInstanceKey(instanceToPredecessorsMap.keySet(), engine.getCallGraph());
			Collection<IDFAState> states = originStreamToMergedTypeStateMap.get(streamInQuestionInstanceKey);
			// Map IDFAState to StreamExecutionMode, etc., and add them to the
			// possible stream states but only if they're not bottom (for those,
			// we fall back to the initial state).
			rule.addPossibleAttributes(this.getStream(), states);
		}

		InstanceKey streamInstanceKey = this.getStream().getInstanceKey(instanceToPredecessorsMap.keySet(),
				engine.getCallGraph());

		// propagate the instances with side-effects.
		instancesWithSideEffects.addAll(instancesWithSideEffects.stream().flatMap(ik -> getAllPredecessors(ik).stream())
				.collect(Collectors.toSet()));

		// propagate the instances with stateful intermediate operations.
		instanceToStatefulIntermediateOperationContainment
				.putAll(instanceToStatefulIntermediateOperationContainment.entrySet().stream().filter(Entry::getValue)
						.map(Entry::getKey).flatMap(ik -> getAllPredecessors(ik).stream())
						.collect(Collectors.toMap(Function.identity(), v -> true)));

		// determine if this stream has possible side-effects.
		this.getStream().setHasPossibleSideEffects(instancesWithSideEffects.contains(streamInstanceKey));

		// determine if this stream has possible stateful intermediate
		// operations.
		this.getStream().setHasPossibleStatefulIntermediateOperations(
				instanceToStatefulIntermediateOperationContainment.getOrDefault(streamInstanceKey, false));

		System.out.println("Execution modes: " + this.getStream().getPossibleExecutionModes());
		System.out.println("Orderings: " + this.getStream().getPossibleOrderings());
		System.out.println("Side-effects: " + this.getStream().hasPossibleSideEffects());
		System.out.println(
				"Stateful intermediate operations: " + this.getStream().hasPossibleStatefulIntermediateOperations());
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

	private static void discoverPossibleStatefulIntermediateOperations(AggregateSolverResult result)
			throws IOException, CoreException {
		// for each instance in the analysis result (these should be the
		// "intermediate" streams).
		for (Iterator<InstanceKey> it = result.iterateInstances(); it.hasNext();) {
			InstanceKey instance = it.next();

			if (!instanceToStatefulIntermediateOperationContainment.containsKey(instance)) {
				CallStringWithReceivers callString = getCallString(instance);

				// make sure that the stream is the result of an intermediate
				// operation.
				if (!isStreamCreatedFromIntermediateOperation(callString))
					continue;

				NormalAllocationInNode allocationInNode = (NormalAllocationInNode) instance;
				MethodReference reference = allocationInNode.getNode().getMethod().getReference();
				boolean statefulIntermediateOperation = isStatefulIntermediateOperation(reference);
				instanceToStatefulIntermediateOperationContainment.put(instance, statefulIntermediateOperation);
			}
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
			for (Iterator<SSAInstruction> it = block.iterator(); it.hasNext();) {
				SSAInstruction instruction = it.next();

				// if it's a phi instruction.
				if (instruction instanceof SSAPhiInstruction)
					continue;

				// number of uses should be 2 for a single explicit param.
				int numberOfUses = instruction.getNumberOfUses();

				if (numberOfUses > 1) {
					// we have a param. Make sure it's no more than one.
					assert numberOfUses == 2 : "Can't handle case where number of uses is: " + numberOfUses;

					// get the first explicit parameter use.
					int paramUse = instruction.getUse(1);
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
			CallStringWithReceivers callString = getCallString(instance);

			// make sure that the stream is the result of an intermediate
			// operation.
			if (!isStreamCreatedFromIntermediateOperation(callString))
				continue;

			CallSiteReference[] callSiteRefs = callString.getCallSiteRefs();
			assert callSiteRefs.length == 2 : "Expecting call sites two-deep.";

			// get the target of the caller.
			MethodReference callerDeclaredTarget = callSiteRefs[1].getDeclaredTarget();
			// get it's IR.
			IMethod callerTargetMethod = engine.getClassHierarchy().resolveMethod(callerDeclaredTarget);
			IR ir = engine.getCache().getIR(callerTargetMethod);

			if (ir == null) {
				Logger.getGlobal().warning(() -> "Can't find IR for target: " + callerTargetMethod);
				continue; // next instance.
			}

			// get calls to the caller target.
			SSAAbstractInvokeInstruction[] calls = ir.getCalls(callSiteRefs[0]);
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

	private static void discoverLambdaSideEffects(EclipseProjectAnalysisEngine<InstanceKey> engine,
			Map<CGNode, OrdinalSet<PointerKey>> mod, Iterable<InstanceKey> instances,
			MethodReference declaredTargetOfCaller, IR ir, int use) {
		// look up it's definition.
		DefUse defUse = engine.getCache().getDefUse(ir);
		// it should be a call.
		SSAAbstractInvokeInstruction def = (SSAAbstractInvokeInstruction) defUse.getDef(use);

		// if we found it.
		if (def != null) {
			// take a look at the nodes in the caller.
			Set<CGNode> nodes = engine.getCallGraph().getNodes(declaredTargetOfCaller);
			assert nodes.size() == 1 : "Expecting only one node here for now (context-sensitivity?). Was: "
					+ nodes.size();

			// for each caller node.
			for (CGNode cgNode : nodes) {
				// for each call site.
				for (Iterator<CallSiteReference> callSiteIt = cgNode.iterateCallSites(); callSiteIt.hasNext();) {
					CallSiteReference callSiteReference = callSiteIt.next();

					// if the call site is the as the one in the
					// behavioral parameter definition.
					if (callSiteReference.equals(def.getCallSite())) {
						// look up the possible target nodes of the call
						// site from the caller.
						Set<CGNode> possibleTargets = engine.getCallGraph().getPossibleTargets(cgNode,
								callSiteReference);
						Logger.getGlobal().info("# possible targets: " + possibleTargets.size());

						if (!possibleTargets.isEmpty()) {
							Logger.getGlobal().info("Possible targets:");
							possibleTargets.forEach(t -> Logger.getGlobal().info(() -> t.toString()));
						}

						// for each possible target node.
						for (CGNode target : possibleTargets) {
							// get the set of pointers (locations) it
							// may modify
							OrdinalSet<PointerKey> modSet = mod.get(target);
							Logger.getGlobal().info("# modified locations: " + modSet.size());

							// if it's non-empty.
							if (!modSet.isEmpty()) {
								Logger.getGlobal().info("Modified locations:");
								modSet.forEach(pk -> Logger.getGlobal().info(() -> pk.toString()));

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
		}
	}

	private static boolean isStreamCreatedFromIntermediateOperation(CallStringWithReceivers callString) {
		Set<InstanceKey> receivers = callString.getPossibleReceivers();

		if (receivers.isEmpty())
			return false;

		// each receiver must be a stream.
		return receivers.stream().allMatch(r -> {
			IClass type = r.getConcreteType();
			return isBaseStream(type)
					|| type.getAllImplementedInterfaces().stream().anyMatch(StreamStateMachine::isBaseStream);
		});
	}

	private static boolean isBaseStream(IClass type) {
		if (type.isInterface()) {
			Atom typePackage = type.getName().getPackage();
			Atom streamPackage = Atom.findOrCreateUnicodeAtom("java/util/stream");
			if (typePackage.equals(streamPackage)) {
				Atom className = type.getName().getClassName();
				Atom baseStream = Atom.findOrCreateUnicodeAtom("BaseStream");
				if (className.equals(baseStream))
					return true;
			}
		}
		return false;
	}

	private static CallStringWithReceivers getCallString(InstanceKey instance) {
		NormalAllocationInNode allocationInNode = (NormalAllocationInNode) instance;
		CGNode node = allocationInNode.getNode();
		CallStringContext context = (CallStringContext) node.getContext();
		CallStringWithReceivers callString = (CallStringWithReceivers) context
				.get(CallStringContextSelector.CALL_STRING);
		return callString;
	}

	/**
	 * The typestate rules to use.
	 */
	protected static StreamAttributeTypestateRule[] createStreamAttributeTypestateRules(IClass streamClass) {
		// @formatter:off
		return new StreamAttributeTypestateRule[] {
				new StreamExecutionModeTypeStateRule(streamClass),
				new StreamOrderingTypeStateRule(streamClass) };
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

	private static Set<IDFAState> computeMergedTypeState(InstanceKey instanceKey,
			BasicBlockInContext<IExplodedBasicBlock> block, StreamAttributeTypestateRule rule) {
		Set<InstanceKey> predecessors = instanceToPredecessorsMap.get(instanceKey);
		Set<IDFAState> possibleInstanceStates = instanceBlockStateTable.get(instanceKey, block);

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

	private static boolean isTerminalOperation(MethodReference method) {
		String signature = method.getSignature();
		return Arrays.stream(TERMINAL_OPERATIONS).anyMatch(signature::startsWith);
	}

	private static boolean isStatefulIntermediateOperation(MethodReference method) {
		String signature = method.getSignature();
		return Arrays.stream(STATEFUL_INTERMEDIATE_OPERATIONS).anyMatch(signature::startsWith);
	}

	protected Stream getStream() {
		return stream;
	}

	public static void clearCaches() {
		instanceBlockStateTable.clear();
		instanceToPredecessorsMap.clear();
		instanceToAllPredecessorsMap.clear();
		originStreamToMergedTypeStateMap.clear();
		instancesWithSideEffects.clear();
		instanceToStatefulIntermediateOperationContainment.clear();
	}
}