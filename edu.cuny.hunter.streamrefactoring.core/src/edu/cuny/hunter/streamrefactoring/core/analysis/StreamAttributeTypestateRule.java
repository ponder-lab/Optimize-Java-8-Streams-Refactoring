package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import com.ibm.safe.dfa.DFASpec;
import com.ibm.safe.dfa.DFAState;
import com.ibm.safe.dfa.DFATransition;
import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.IDFATransition;
import com.ibm.safe.dfa.events.IDispatchEvent;
import com.ibm.safe.dfa.events.IDispatchEventImpl;
import com.ibm.safe.rules.TypestateRule;
import com.ibm.wala.classLoader.IClass;

public abstract class StreamAttributeTypestateRule extends TypestateRule {

	protected static final String BOTTOM_STATE_NAME = "bottom";

	protected IDFAState bottomState;

	public StreamAttributeTypestateRule(IClass streamClass, String name) {
		this.addType(streamClass.getName().toString());
		this.setName(name);
		this.setTypeStateAutomaton(new DFASpec());
		this.addAutomaton();
	}

	protected void addAutomaton() {
		// a bottom state result would need to defer to the initial stream
		// ordering, which is in the field of the stream.
		bottomState = addState(BOTTOM_STATE_NAME, true);
	}

	protected IDFAState addState(String stateName, boolean initialState) {
		IDFAState state = new DFAState();

		state.setName(stateName);
		this.getTypeStateAutomaton().addState(state);

		if (initialState)
			this.getTypeStateAutomaton().setInitialState(state);

		return state;
	}

	protected IDFATransition addTransition(IDFAState source, IDFAState destination, IDispatchEvent event) {
		IDFATransition transition = new DFATransition();

		transition.setSource(source);
		transition.setEvent(event);
		transition.setDestination(destination);

		this.getTypeStateAutomaton().addTransition(transition);
		return transition;
	}

	protected IDispatchEvent addEvent(String eventName, String eventPattern) {
		IDispatchEventImpl event = new IDispatchEventImpl();

		event.setName(eventName);
		event.setPattern(eventPattern);

		this.getTypeStateAutomaton().addEvent(event);
		return event;
	}

	protected IDFAState addState(String stateName) {
		return addState(stateName, false);
	}

	protected IDFAState addState(Enum<?> constant) {
		return this.addState(constant.name().toLowerCase());
	}

	public IDFAState getBottomState() {
		return bottomState;
	}

	protected void addPossibleAttributes(Stream stream, Collection<IDFAState> states) {
		Objects.requireNonNull(stream);
		Objects.requireNonNull(states);
	}
}