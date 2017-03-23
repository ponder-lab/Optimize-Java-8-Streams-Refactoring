package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.safe.Factoid;
import com.ibm.safe.ICFGSupergraph;
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
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallString;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContext;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFABuilder;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.IntSet;

import edu.cuny.hunter.streamrefactoring.core.analysis.rules.StreamExecutionModeTypeStateRule;
import edu.cuny.hunter.streamrefactoring.core.safe.ModifiedBenignOracle;
import edu.cuny.hunter.streamrefactoring.core.safe.TrackingUniqueSolver;
import edu.cuny.hunter.streamrefactoring.core.safe.TypestateSolverFactory;
import edu.cuny.hunter.streamrefactoring.core.wala.CallStringWithReceivers;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

class StateMachine {

	/**
	 * The stream that this state machine represents.
	 */
	private final Stream stream;

	/**
	 * Constructs a new {@link StateMachine} given a {@link Stream} to
	 * represent.
	 * 
	 * @param stream
	 *            The representing stream.
	 */
	StateMachine(Stream stream) {
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

		// this gets a solver that tracks all streams.
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
			Logger.getGlobal().info("Getting results for: " + instanceKey);

			TypeStateResult instanceResult = (TypeStateResult) result.getInstanceResult(instanceKey);
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

					System.out.println(callString);
					System.out.println(node);
					System.out.println("Receivers:");
					callString.getPossibleReceivers().forEach(System.out::println);

					CallSiteReference[] callSiteRefs = callString.getCallSiteRefs();
					IMethod[] methods = callString.getMethods();

					System.out.println(callSiteRefs[0].getProgramCounter());
					System.out.println(methods[0]);

					SSAPropagationCallGraphBuilder builder = (SSAPropagationCallGraphBuilder) engine
							.getCallGraphBuilder();
					int predNodeCount = engine.getCallGraph().getPredNodeCount(node);
					System.out.println(predNodeCount);
					Iterator<CGNode> predNodes = engine.getCallGraph().getPredNodes(node);
					predNodes.forEachRemaining(n -> {
						System.out.println(n);
						Context target = builder.getContextSelector().getCalleeTarget(n, callSiteRefs[0], methods[0],
								null);
						System.out.println(target);
					});

					// ExplicitCallGraph callGraph = (ExplicitCallGraph)
					// solver.getCallGraph();
					// callGraph.target

					Logger.getGlobal().info(instance.toString());
					Logger.getGlobal().info(uniqueFactoid.state.toString());
				}
			}
		}

		// InstanceKey streamInstanceKey =
		// this.getStream().getInstanceKey(solver.getTrackedInstances(),
		// solver.getCallGraph());
		InstanceKey streamInstanceKey = solver.getTrackedInstances().iterator().next();
		System.out.println("Stream instance key: " + streamInstanceKey);

		TypeStateResult instanceResult = (TypeStateResult) result.getInstanceResult(streamInstanceKey);

		ICFGSupergraph supergraph = instanceResult.getSupergraph();
		// FIXME This doesn't make a whole lot of sense. Only looking at the
		// node of where the stream was declared:
		Set<CGNode> cgNodes = engine.getCallGraph().getNodes(this.getStream().getEnclosingMethodReference());

		// TODO: Derive state at point of consumption?
		cgNodes.forEach(cgNode -> {
			BasicBlockInContext<IExplodedBasicBlock> block = supergraph.getLocalBlock(cgNode, 10);
			IntSet resultIntSet = instanceResult.getResult().getResult(block);

			// print out the facts at the node.
			resultIntSet.foreach(i -> System.out.println(instanceResult.getDomain().getMappedObject(i)));
		});

	}

	protected Stream getStream() {
		return stream;
	}
}