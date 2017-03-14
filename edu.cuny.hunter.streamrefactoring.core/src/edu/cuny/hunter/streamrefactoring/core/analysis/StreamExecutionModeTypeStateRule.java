package edu.cuny.hunter.streamrefactoring.core.analysis;

import com.ibm.safe.dfa.DFASpec;
import com.ibm.safe.dfa.DFAState;
import com.ibm.safe.dfa.DFATransition;
import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.IDFATransition;
import com.ibm.safe.dfa.events.IDispatchEvent;
import com.ibm.safe.dfa.events.IDispatchEventImpl;
import com.ibm.safe.rules.TypestateRule;
import com.ibm.wala.classLoader.IClass;

public class StreamExecutionModeTypeStateRule extends TypestateRule {

	public StreamExecutionModeTypeStateRule(IClass streamClass) {
		super();
		this.addType(streamClass.getName().toString());
		this.setName("execution mode");
		this.addAutomaton();
	}

	private void addAutomaton() {
		DFASpec automaton = new DFASpec();

		// a bottom state result would need to defer to the initial stream
		// ordering, which is in the field of the stream.
		IDFAState bottomBottomState = addState(automaton, "bottomBottom", true);

		IDFAState sequentialBottomState = addState(automaton, "sequentialBottom");
		IDFAState parallelBottomState = addState(automaton, "parallelBottom");
		IDFAState bottomOrderedState = addState(automaton, "bottomOrdered");
		IDFAState bottomUnorderedState = addState(automaton, "bottomUnordered");

		IDFAState sequentialOrderedState = addState(automaton, "sequentialOrdered");
		IDFAState sequentialUnorderedState = addState(automaton, "sequentialUnordered");
		IDFAState parallelOrderedState = addState(automaton, "parallelOrdered");
		IDFAState parallelUnorderedState = addState(automaton, "parallelUnordered");

		IDispatchEvent parallelEvent = addEvent(automaton, "parallel", ".*parallel\\(\\).*");
		IDispatchEvent sequentialEvent = addEvent(automaton, "sequential", ".*sequential\\(\\).*");
		IDispatchEvent sortedEvent = addEvent(automaton, "sorted", ".*sorted\\(\\).*");
		IDispatchEvent unorderedEvent = addEvent(automaton, "unordered", ".*unordered\\(\\).*");

		// TODO: Need to add concat().
		addTransition(automaton, bottomBottomState, parallelBottomState, parallelEvent);
		addTransition(automaton, bottomBottomState, sequentialBottomState, sequentialEvent);
		addTransition(automaton, bottomBottomState, bottomOrderedState, sortedEvent);
		addTransition(automaton, bottomBottomState, bottomUnorderedState, unorderedEvent);

		addTransition(automaton, sequentialBottomState, parallelBottomState, parallelEvent);
		addTransition(automaton, sequentialBottomState, sequentialBottomState, sequentialEvent);
		addTransition(automaton, sequentialBottomState, sequentialOrderedState, sortedEvent);
		addTransition(automaton, sequentialBottomState, sequentialUnorderedState, unorderedEvent);

		addTransition(automaton, parallelBottomState, parallelBottomState, parallelEvent);
		addTransition(automaton, parallelBottomState, sequentialBottomState, sequentialEvent);
		addTransition(automaton, parallelBottomState, parallelOrderedState, sortedEvent);
		addTransition(automaton, parallelBottomState, parallelUnorderedState, unorderedEvent);

		addTransition(automaton, bottomUnorderedState, parallelUnorderedState, parallelEvent);
		addTransition(automaton, bottomUnorderedState, sequentialUnorderedState, sequentialEvent);
		addTransition(automaton, bottomUnorderedState, bottomOrderedState, sortedEvent);
		addTransition(automaton, bottomUnorderedState, bottomUnorderedState, unorderedEvent);

		addTransition(automaton, bottomOrderedState, parallelOrderedState, parallelEvent);
		addTransition(automaton, bottomOrderedState, sequentialOrderedState, sequentialEvent);
		addTransition(automaton, bottomOrderedState, bottomOrderedState, sortedEvent);
		addTransition(automaton, bottomOrderedState, bottomUnorderedState, unorderedEvent);

		addTransition(automaton, sequentialUnorderedState, parallelUnorderedState, parallelEvent);
		addTransition(automaton, sequentialUnorderedState, sequentialUnorderedState, sequentialEvent);
		addTransition(automaton, sequentialUnorderedState, sequentialOrderedState, sortedEvent);
		addTransition(automaton, sequentialUnorderedState, sequentialUnorderedState, unorderedEvent);

		addTransition(automaton, sequentialOrderedState, parallelOrderedState, parallelEvent);
		addTransition(automaton, sequentialOrderedState, sequentialOrderedState, sequentialEvent);
		addTransition(automaton, sequentialOrderedState, sequentialOrderedState, sortedEvent);
		addTransition(automaton, sequentialOrderedState, sequentialUnorderedState, unorderedEvent);

		addTransition(automaton, parallelUnorderedState, parallelUnorderedState, parallelEvent);
		addTransition(automaton, parallelUnorderedState, sequentialUnorderedState, sequentialEvent);
		addTransition(automaton, parallelUnorderedState, parallelOrderedState, sortedEvent);
		addTransition(automaton, parallelUnorderedState, parallelUnorderedState, unorderedEvent);

		addTransition(automaton, parallelOrderedState, parallelOrderedState, parallelEvent);
		addTransition(automaton, parallelOrderedState, sequentialOrderedState, sequentialEvent);
		addTransition(automaton, parallelOrderedState, parallelOrderedState, sortedEvent);
		addTransition(automaton, parallelOrderedState, parallelUnorderedState, unorderedEvent);

		this.setTypeStateAutomaton(automaton);
	}

	private static IDFATransition addTransition(DFASpec automaton, IDFAState source, IDFAState destination,
			IDispatchEvent event) {
		IDFATransition transition = new DFATransition();
		transition.setSource(source);
		transition.setEvent(event);
		transition.setDestination(destination);
		automaton.addTransition(transition);
		return transition;
	}

	private static IDispatchEvent addEvent(DFASpec automaton, String eventName, String eventPattern) {
		IDispatchEventImpl close = new IDispatchEventImpl();
		close.setName(eventName);
		close.setPattern(eventPattern);
		automaton.addEvent(close);
		return close;
	}

	private static IDFAState addState(DFASpec automaton, String stateName) {
		return addState(automaton, stateName, false);
	}

	private static IDFAState addState(DFASpec automaton, String stateName, boolean initialState) {
		IDFAState state = new DFAState();

		state.setName(stateName);
		automaton.addState(state);

		if (initialState)
			automaton.setInitialState(state);

		return state;
	}

}
