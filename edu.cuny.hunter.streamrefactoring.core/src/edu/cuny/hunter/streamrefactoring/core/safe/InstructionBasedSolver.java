package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Logger;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.reporting.IReporter;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.merge.IMergeFunctionFactory;
import com.ibm.safe.typestate.metrics.TypeStateMetrics;
import com.ibm.safe.typestate.mine.TraceReporter;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.safe.typestate.unique.UniqueSolver;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.escape.ILiveObjectAnalysis;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallString;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContextSelector;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.collections.Pair;

public class InstructionBasedSolver extends UniqueSolver {

	private SSAInvokeInstruction instruction;

	public InstructionBasedSolver(CallGraph cg, PointerAnalysis<?> pointerAnalysis, TypeStateProperty property,
			TypeStateOptions options, ILiveObjectAnalysis live, BenignOracle ora, TypeStateMetrics metrics,
			IReporter reporter, TraceReporter traceReporter, IMergeFunctionFactory mergeFactory,
			SSAInvokeInstruction instruction) {
		super(cg, pointerAnalysis, property, options, live, ora, metrics, reporter, traceReporter, mergeFactory);
		this.instruction = instruction;
	}

	@Override
	protected Collection<InstanceKey> computeTrackedInstances() throws PropertiesException {
		Collection<InstanceKey> ret = new HashSet<>();

		// compute all instances whose type is tracked by the DFA.
		Collection<InstanceKey> trackedInstancesByType = this.computeTrackedInstancesByType();

		for (InstanceKey instanceKey : trackedInstancesByType) {
			Iterator<Pair<CGNode, NewSiteReference>> creationSites = instanceKey.getCreationSites(this.getCallGraph());

			while (creationSites.hasNext()) {
				Pair<CGNode, NewSiteReference> pair = creationSites.next();
				ContextItem contextItem = pair.fst.getContext().get(CallStringContextSelector.CALL_STRING);
				CallString callString = (CallString) contextItem;
				CallSiteReference[] callSiteRefs = callString.getCallSiteRefs();

				for (CallSiteReference callSiteReference : callSiteRefs)
					if (callSiteReference.equals(this.getInstruction().getCallSite())) {
						ret.add(instanceKey);
						break;
					}
			}
		}

		if (ret.size() != 1)
			throw new IllegalStateException("Tracking more or less than one instance: " + ret.size());
		
		Logger.getGlobal().info("Tracking: " + ret);
		return ret;
	}

	protected SSAInvokeInstruction getInstruction() {
		return this.instruction;
	}
}
