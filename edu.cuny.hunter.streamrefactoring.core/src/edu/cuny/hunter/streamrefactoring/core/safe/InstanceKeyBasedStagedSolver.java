package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.Collection;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.perf.PerformanceTracker;
import com.ibm.safe.reporting.IReporter;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.metrics.TypeStateMetrics;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.safe.typestate.staged.StagedSolver;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.SSAInvokeInstruction;

public class InstanceKeyBasedStagedSolver extends StagedSolver {

	private SSAInvokeInstruction creation;

	public InstanceKeyBasedStagedSolver(AnalysisOptions domoOptions, CallGraph cg, PointerAnalysis<InstanceKey> pointerAnalysis,
			TypeStateProperty property, TypeStateOptions options, BenignOracle ora, TypeStateMetrics metrics,
			IReporter reporter, PerformanceTracker perfTracker, SSAInvokeInstruction creation) {
		super(domoOptions, cg, pointerAnalysis, property, options, ora, metrics, reporter, perfTracker);
		this.creation = creation;
	}

	@Override
	protected Collection<InstanceKey> computeTrackedInstances() throws PropertiesException {
		Collection<InstanceKey> result = computeTrackedInstancesByCreation();	
		
		
		// TODO Auto-generated method stub
		return super.computeTrackedInstances();
	}

	private Collection<InstanceKey> computeTrackedInstancesByCreation() {
		for (Object object : this.getPointerAnalysis().getPointerKeys()) {
			PointerKey pointerKey = (PointerKey) object;

			
		}
		// TODO Auto-generated method stub
		return null;
	}
}
