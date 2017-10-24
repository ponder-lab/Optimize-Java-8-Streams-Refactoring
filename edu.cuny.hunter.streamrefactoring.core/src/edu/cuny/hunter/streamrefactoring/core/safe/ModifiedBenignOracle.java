package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.collections.Pair;

import edu.cuny.hunter.streamrefactoring.core.utils.Packages;

public class ModifiedBenignOracle extends BenignOracle {
	
	private static final Logger LOGGER = Logger.getLogger(Packages.streamRefactoring);

	public ModifiedBenignOracle(CallGraph callGraph, PointerAnalysis<?> pointerAnalysis) {
		super(callGraph, pointerAnalysis);
	}

	@Override
	public void addBenignInstanceKey(InstanceKey ik) {
		LOGGER.info(() -> "Was requested to ignore \"benign\" instance with key: " + ik);
	}

	@Override
	public Map<InstanceKey, Set<Pair<CGNode, SSAInstruction>>> possibleErrorLocations(TypeStateProperty property) {
		return Collections.emptyMap();
	}

}
