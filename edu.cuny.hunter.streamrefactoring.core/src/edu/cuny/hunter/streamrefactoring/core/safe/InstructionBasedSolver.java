package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.Collection;
import java.util.HashSet;
import java.util.logging.Logger;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.reporting.IReporter;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.merge.IMergeFunctionFactory;
import com.ibm.safe.typestate.metrics.TypeStateMetrics;
import com.ibm.safe.typestate.mine.TraceReporter;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.wala.escape.ILiveObjectAnalysis;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ssa.SSAInvokeInstruction;

public class InstructionBasedSolver extends TrackingUniqueSolver {

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
			Logger.getGlobal().info("Examining instance: " + instanceKey);
			if (Util.instanceKeyCorrespondsWithInstantiationInstruction(instanceKey, this.getInstruction(),
					this.getCallGraph()))
				ret.add(instanceKey);
		}

		if (ret.size() != 1)
			throw new IllegalStateException("Tracking more or less than one instance: " + ret.size());

		Logger.getGlobal().info("Tracking: " + ret);
		this.setTrackedInstances(ret);
		return ret;
	}

	protected SSAInvokeInstruction getInstruction() {
		return this.instruction;
	}
}
