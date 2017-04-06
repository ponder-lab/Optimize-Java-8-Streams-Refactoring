package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

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
import com.ibm.safe.rules.TypestateRule;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateDomain;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.core.TypeStateResult;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.safe.typestate.unique.UniqueFactoid;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContext;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContextSelector;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
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

		TypestateRule rule = new StreamExecutionModeTypeStateRule(streamClass);
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

		for (Iterator<InstanceKey> iterator = result.iterateInstances(); iterator.hasNext();) {
			InstanceKey instanceKey = iterator.next();
			TypeStateResult instanceResult = (TypeStateResult) result.getInstanceResult(instanceKey);
			ICFGSupergraph supergraph = instanceResult.getSupergraph();
			// FIXME This doesn't make a whole lot of sense. Only looking at the
			// node of where the stream was declared:
			Set<CGNode> cgNodes = engine.getCallGraph().getNodes(this.getStream().getEnclosingMethodReference());
			assert cgNodes.size() == 1 : "Expecting only a single CG node.";

			for (CGNode cgNode : cgNodes) {
				for (Iterator<CallSiteReference> callSites = cgNode.iterateCallSites(); callSites.hasNext();) {
					CallSiteReference callSiteReference = callSites.next();
					String calledMethodSignature = callSiteReference.getDeclaredTarget().getSignature();

					// is it a terminal operation?
					if (isTerminalOperation(calledMethodSignature)) {
						// FIXME: But, who is the receiver?

						// get the basic block for the call.
						ISSABasicBlock[] blocksForCall = cgNode.getIR().getBasicBlocksForCall(callSiteReference);
						assert blocksForCall.length == 1 : "Expecting only a single basic block for the call: "
								+ callSiteReference;

						for (int i = 0; i < blocksForCall.length; i++) {
							ISSABasicBlock block = blocksForCall[i];
							BasicBlockInContext<IExplodedBasicBlock> blockInContext = supergraph.getLocalBlock(cgNode,
									block.getNumber());

							IntSet intSet = instanceResult.getResult().getResult(blockInContext);
							for (IntIterator it = intSet.intIterator(); it.hasNext();) {
								int nextInt = it.next();
								Factoid factoid = instanceResult.getDomain().getMappedObject(nextInt);

								if (factoid instanceof UniqueFactoid) {
									UniqueFactoid uniqueFactoid = (UniqueFactoid) factoid;

									// retrieve the state set for this instance
									// and block.
									Set<IDFAState> stateSet = instanceBlockToStateTable.get(uniqueFactoid.instance,
											blockInContext);

									// if it does not yet exist.
									if (stateSet == null) {
										// allocate a new set.
										stateSet = new HashSet<>();

										// place it in the table.
										instanceBlockToStateTable.put(uniqueFactoid.instance, blockInContext, stateSet);
									}

									// add the encountered state to the set.
									stateSet.add(uniqueFactoid.state);
								}
							}
						}
					}
				}
			}

			TypeStateDomain domain = instanceResult.getDomain();
			Collection<Factoid> objects = domain.getObjects();
			for (Factoid factoid : objects) {
				if (factoid instanceof UniqueFactoid) {
					UniqueFactoid uniqueFactoid = (UniqueFactoid) factoid;
					InstanceKey instance = uniqueFactoid.instance;
					NormalAllocationInNode allocationInNode = (NormalAllocationInNode) instance;
					CGNode node = allocationInNode.getNode();

					CallStringContext context = (CallStringContext) node.getContext();
					CallStringWithReceivers callString = (CallStringWithReceivers) context
							.get(CallStringContextSelector.CALL_STRING);

					Logger.getGlobal().info(instance.toString());
					Logger.getGlobal().info(uniqueFactoid.state.toString());
				}
			}
		}

		System.out.println(instanceBlockToStateTable);
	}

	private boolean isTerminalOperation(String calledMethodSignature) {
		return Arrays.stream(TERMINAL_OPERATIONS).anyMatch(to -> calledMethodSignature.startsWith(to));
	}

	protected Stream getStream() {
		return stream;
	}

	public static void clearCaches() {
		instanceBlockToStateTable.clear();
	}
}