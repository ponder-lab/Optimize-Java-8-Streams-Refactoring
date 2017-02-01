package edu.cuny.hunter.streamrefactoring.core.rules;

import java.util.Collection;
import java.util.Set;

import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.events.IEvent;
import com.ibm.safe.rules.TypestateRule;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.rules.AbstractTypeStateDFA;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class EventTrackingTypeStateProperty extends TypeStateProperty {

	public EventTrackingTypeStateProperty(IClassHierarchy cha, Collection<IClass> types) {
		super(cha);
		types.stream().forEach(this::addType);
		// TODO Auto-generated constructor stub
	}

	public EventTrackingTypeStateProperty(TypestateRule aTypeStateRule, IClassHierarchy cha) {
		super(aTypeStateRule, cha);
		// TODO Auto-generated constructor stub
	}

	protected EventTrackingTypeStateProperty(IClassHierarchy cha) {
		super(cha);
		// TODO Auto-generated constructor stub
	}

	@Override
	public IDFAState successor(IDFAState state, IEvent e) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<IDFAState> predecessors(IDFAState state, IEvent automatonLabel) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IDFAState initial() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IEvent match(Class eventClass, String param) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String toString() {
		return this.getClass().getName();
	}
}
