package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.Collection;
import java.util.Set;

import com.ibm.safe.dfa.DFASpec;
import com.ibm.safe.dfa.DFAState;
import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.events.IEvent;
import com.ibm.safe.rules.TypestateRule;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class EventTrackingTypeStateProperty extends TypeStateProperty {

	private static TypestateRule getTypeStateRule(Collection<IClass> types) {
		TypestateRule rule = new TypestateRule();
		types.stream().map(t -> t.getName()).map(n -> n.toString()).forEach(rule::addType);
		DFASpec newTypeStateAutomaton = new DFASpec();
		rule.setTypeStateAutomaton(newTypeStateAutomaton);

		return rule;
	}

	protected EventTrackingTypeStateProperty(IClassHierarchy cha) {
		super(cha);
	}

	public EventTrackingTypeStateProperty(IClassHierarchy cha, Collection<IClass> types) {
		super(getTypeStateRule(types), cha);
		types.stream().forEach(this::addType);
	}

	public EventTrackingTypeStateProperty(TypestateRule aTypeStateRule, IClassHierarchy cha) {
		super(aTypeStateRule, cha);
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IDFAState initial() {
		DFAState state = new DFAState();
		state.setAccepting(true);
		state.setName("Initial?");
		return state;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public IEvent match(Class eventClass, String param) {
		// TODO Auto-generated method stub
		System.out.println("MATCH: " + eventClass + ": " + param);
		return null;// super.match(eventClass, param);
	}

	@Override
	public Set<IDFAState> predecessors(IDFAState state, IEvent automatonLabel) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IDFAState successor(IDFAState state, IEvent e) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String toString() {
		return this.getClass().getName();
	}
}
