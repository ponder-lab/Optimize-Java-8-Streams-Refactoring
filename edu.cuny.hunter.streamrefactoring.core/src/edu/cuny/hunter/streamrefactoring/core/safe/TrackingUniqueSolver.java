package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.Collection;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.reporting.IReporter;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.merge.IMergeFunctionFactory;
import com.ibm.safe.typestate.metrics.TypeStateMetrics;
import com.ibm.safe.typestate.mine.TraceReporter;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.safe.typestate.rules.ITypeStateDFA;
import com.ibm.safe.typestate.unique.UniqueSolver;
import com.ibm.wala.escape.ILiveObjectAnalysis;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;

// TODO: Need to change the super class. UniqueSolver isn't sound.
public class TrackingUniqueSolver extends UniqueSolver {

	private Collection<InstanceKey> trackedInstances;

	public TrackingUniqueSolver(CallGraph cg, PointerAnalysis pointerAnalysis, ITypeStateDFA dfa,
			TypeStateOptions options, ILiveObjectAnalysis live, BenignOracle ora, TypeStateMetrics metrics,
			IReporter reporter, TraceReporter traceReporter, IMergeFunctionFactory mergeFactory) {
		super(cg, pointerAnalysis, dfa, options, live, ora, metrics, reporter, traceReporter, mergeFactory);
	}

	public Collection<InstanceKey> getTrackedInstances() {
		return trackedInstances;
	}

	protected void setTrackedInstances(Collection<InstanceKey> trackedInstances) {
		this.trackedInstances = trackedInstances;
	}

	@Override
	protected Collection<InstanceKey> computeTrackedInstances() throws PropertiesException {
		Collection<InstanceKey> instances = super.computeTrackedInstances();
		this.setTrackedInstances(instances);
		return instances;
	}

}