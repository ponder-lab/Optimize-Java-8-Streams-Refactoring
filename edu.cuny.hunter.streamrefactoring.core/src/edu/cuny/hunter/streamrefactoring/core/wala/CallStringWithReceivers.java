package edu.cuny.hunter.streamrefactoring.core.wala;

import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallString;

public class CallStringWithReceivers extends CallString {

	private Set<InstanceKey> possibleReceivers = new HashSet<>();

	public CallStringWithReceivers(CallSiteReference site, IMethod method) {
		super(site, method);
	}

	public CallStringWithReceivers(CallSiteReference site, IMethod method, int length, CallString callString) {
		super(site, method, length, callString);
	}
	
	public void addPossibleReceiver(InstanceKey receiver) {
		this.getPossibleReceivers().add(receiver);
	}

	public Set<InstanceKey> getPossibleReceivers() {
		return this.possibleReceivers;
	}
}