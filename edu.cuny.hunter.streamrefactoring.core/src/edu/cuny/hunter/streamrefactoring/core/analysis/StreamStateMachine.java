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
import java.util.Set;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.ibm.safe.Factoid;
import com.ibm.safe.ICFGSupergraph;
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
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;

import edu.cuny.hunter.streamrefactoring.core.analysis.rules.StreamAttributeTypestateRule;
import edu.cuny.hunter.streamrefactoring.core.analysis.rules.StreamExecutionModeTypeStateRule;
import edu.cuny.hunter.streamrefactoring.core.safe.ModifiedBenignOracle;
import edu.cuny.hunter.streamrefactoring.core.safe.TrackingUniqueSolver;
import edu.cuny.hunter.streamrefactoring.core.safe.TypestateSolverFactory;
import edu.cuny.hunter.streamrefactoring.core.wala.CallStringWithReceivers;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

class StreamStateMachine {

	private static final String JAVA_UTIL_STREAM_STREAM_REDUCE = "java.util.stream.Stream.reduce";

	private static final String[] TERMINAL_OPERATIONS = { JAVA_UTIL_STREAM_STREAM_REDUCE };

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
		EclipseProjectAnalysisEngine<InstanceKey> engine = this.getStream().getAnalysisEngine();

		// FIXME: Do we want a different entry point?
		DefaultEntrypoint entryPoint = new DefaultEntrypoint(this.getStream().getEnclosingMethodReference(),
				this.getStream().getClassHierarchy());
		Set<Entrypoint> entryPoints = Collections.singleton(entryPoint);

		// FIXME: Do we need to build a new call graph for each entry point?
		// Doesn't make sense. Maybe we need to collect all enclosing
		// methods and use those as entry points.
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

		StreamAttributeTypestateRule rule = new StreamExecutionModeTypeStateRule(streamClass);
		TypeStateProperty dfa = new TypeStateProperty(rule, engine.getClassHierarchy());

		// this gets a solver that tracks all streams. TODO may need to do some
		// caching at some point here.
		TrackingUniqueSolver solver = TypestateSolverFactory.getSolver(engine.getCallGraph(),
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

			// FIXME This doesn't make a whole lot of sense. Only looking at the
			// node of where the stream was declared:
			// TODO: Can this be somehow rewritten to get blocks corresponding
			// to terminal operations?
			Set<CGNode> cgNodes = engine.getCallGraph().getNodes(this.getStream().getEnclosingMethodReference());
			assert cgNodes.size() == 1 : "Expecting only a single CG node.";

			for (CGNode cgNode : cgNodes) {
				for (Iterator<CallSiteReference> callSites = cgNode.iterateCallSites(); callSites.hasNext();) {
					CallSiteReference callSiteReference = callSites.next();
					MethodReference calledMethod = callSiteReference.getDeclaredTarget();

					// is it a terminal operation?
					if (isTerminalOperation(calledMethod)) {
						// get the basic block for the call.
						ISSABasicBlock[] blocksForCall = cgNode.getIR().getBasicBlocksForCall(callSiteReference);
						assert blocksForCall.length == 1 : "Expecting only a single basic block for the call: "
								+ callSiteReference;

						for (int i = 0; i < blocksForCall.length; i++) {
							ISSABasicBlock block = blocksForCall[i];
							BasicBlockInContext<IExplodedBasicBlock> blockInContext = supergraph.getLocalBlock(cgNode,
									block.getNumber());

							if (!terminalBlockToPossibleReceivers.containsKey(blockInContext)) {
								// associate possible receivers with the
								// blockInContext.
								// search through each instruction in the block.
								for (Iterator<SSAInstruction> it = block.iterator(); it.hasNext();) {
									SSAInstruction instruction = it.next();
									assert !it.hasNext() : "Expecting only a single instruction for the block: "
											+ block;

									// Get the possible receivers.
									// This number corresponds to the value
									// number of the receiver of the method.
									int valueNumberForReceiver = instruction.getUse(0);

									// it should be represented by a pointer
									// key.
									PointerKey pointerKey = engine.getHeapGraph().getHeapModel()
											.getPointerKeyForLocal(cgNode, valueNumberForReceiver);

									// get the points to set for the receiver.
									// This will give us all object instances
									// that the receiver reference points to.
									OrdinalSet<InstanceKey> pointsToSet = engine.getPointerAnalysis()
											.getPointsToSet(pointerKey);

									terminalBlockToPossibleReceivers.put(blockInContext, pointsToSet);
								}
							}

							IntSet intSet = instanceResult.getResult().getResult(blockInContext);
							for (IntIterator it = intSet.intIterator(); it.hasNext();) {
								int nextInt = it.next();

								// retrieve the state set for this instance
								// and block.
								Set<IDFAState> stateSet = instanceBlockToStateTable.get(instanceKey, blockInContext);

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
									assert baseFactoid.instance == instanceKey : "Sanity check that the fact instance should be the same as the instance being examined.";

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

		// for each terminal operation call, I think?
		for (BasicBlockInContext<IExplodedBasicBlock> block : terminalBlockToPossibleReceivers.keySet()) {
			OrdinalSet<InstanceKey> possibleReceivers = terminalBlockToPossibleReceivers.get(block);
			// for each possible receiver of the terminal operation call.
			for (InstanceKey instanceKey : possibleReceivers) {
				Set<IDFAState> possibleStates = computeMergedTypeState(instanceKey, block, rule);
				System.out.println(instanceKey + ": " + possibleStates);
				// TODO: But how do we integrate this? Do we fill out a map or
				// something? We need to know if this impacts the original
				// stream, right? Is the receiver tied to the original stream?
			}
		}
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
		return Arrays.stream(TERMINAL_OPERATIONS).anyMatch(to -> calledMethod.getSignature().startsWith(to));
	}

	protected Stream getStream() {
		return stream;
	}

	public static void clearCaches() {
		instanceBlockToStateTable.clear();
		instanceToPredecessorMap.clear();
	}
}