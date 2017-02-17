package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.logging.Logger;

import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;

public class ModifiedBenignOracle extends BenignOracle {

	public ModifiedBenignOracle(CallGraph callGraph, PointerAnalysis pointerAnalysis) {
		super(callGraph, pointerAnalysis);
	}

	@Override
	public void addBenignInstanceKey(InstanceKey ik) {
		Logger.getGlobal().info(() -> "Ignoring benign instance key: " + ik);
	}

}
