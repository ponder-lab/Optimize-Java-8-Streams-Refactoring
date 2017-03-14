package edu.cuny.hunter.streamrefactoring.core.analysis.rules;

import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.events.IDispatchEvent;
import com.ibm.wala.classLoader.IClass;

public class StreamExecutionModeTypeStateRule extends StreamAttributeTypestateRule {

	public StreamExecutionModeTypeStateRule(IClass streamClass) {
		super(streamClass);
		this.setName("execution mode");
	}

	@Override
	protected void addAutomaton() {
		// a bottom state result would need to defer to the initial stream
		// ordering, which is in the field of the stream.
		IDFAState bottomBottomState = addState("bottomBottom", true);
				
		IDFAState sequentialBottomState = addState("sequentialBottom");
		IDFAState parallelBottomState = addState("parallelBottom");
		IDFAState bottomOrderedState = addState("bottomOrdered");
		IDFAState bottomUnorderedState = addState("bottomUnordered");

		IDFAState sequentialOrderedState = addState("sequentialOrdered");
		IDFAState sequentialUnorderedState = addState("sequentialUnordered");
		IDFAState parallelOrderedState = addState("parallelOrdered");
		IDFAState parallelUnorderedState = addState("parallelUnordered");

		IDispatchEvent parallelEvent = addEvent("parallel", ".*parallel\\(\\).*");
		IDispatchEvent sequentialEvent = addEvent("sequential", ".*sequential\\(\\).*");
		IDispatchEvent sortedEvent = addEvent("sorted", ".*sorted\\(\\).*");
		IDispatchEvent unorderedEvent = addEvent("unordered", ".*unordered\\(\\).*");

		// TODO: Need to add concat().
		addTransition(bottomBottomState, parallelBottomState, parallelEvent);
		addTransition(bottomBottomState, sequentialBottomState, sequentialEvent);
		addTransition(bottomBottomState, bottomOrderedState, sortedEvent);
		addTransition(bottomBottomState, bottomUnorderedState, unorderedEvent);

		addTransition(sequentialBottomState, parallelBottomState, parallelEvent);
		addTransition(sequentialBottomState, sequentialBottomState, sequentialEvent);
		addTransition(sequentialBottomState, sequentialOrderedState, sortedEvent);
		addTransition(sequentialBottomState, sequentialUnorderedState, unorderedEvent);

		addTransition(parallelBottomState, parallelBottomState, parallelEvent);
		addTransition(parallelBottomState, sequentialBottomState, sequentialEvent);
		addTransition(parallelBottomState, parallelOrderedState, sortedEvent);
		addTransition(parallelBottomState, parallelUnorderedState, unorderedEvent);

		addTransition(bottomUnorderedState, parallelUnorderedState, parallelEvent);
		addTransition(bottomUnorderedState, sequentialUnorderedState, sequentialEvent);
		addTransition(bottomUnorderedState, bottomOrderedState, sortedEvent);
		addTransition(bottomUnorderedState, bottomUnorderedState, unorderedEvent);

		addTransition(bottomOrderedState, parallelOrderedState, parallelEvent);
		addTransition(bottomOrderedState, sequentialOrderedState, sequentialEvent);
		addTransition(bottomOrderedState, bottomOrderedState, sortedEvent);
		addTransition(bottomOrderedState, bottomUnorderedState, unorderedEvent);

		addTransition(sequentialUnorderedState, parallelUnorderedState, parallelEvent);
		addTransition(sequentialUnorderedState, sequentialUnorderedState, sequentialEvent);
		addTransition(sequentialUnorderedState, sequentialOrderedState, sortedEvent);
		addTransition(sequentialUnorderedState, sequentialUnorderedState, unorderedEvent);

		addTransition(sequentialOrderedState, parallelOrderedState, parallelEvent);
		addTransition(sequentialOrderedState, sequentialOrderedState, sequentialEvent);
		addTransition(sequentialOrderedState, sequentialOrderedState, sortedEvent);
		addTransition(sequentialOrderedState, sequentialUnorderedState, unorderedEvent);

		addTransition(parallelUnorderedState, parallelUnorderedState, parallelEvent);
		addTransition(parallelUnorderedState, sequentialUnorderedState, sequentialEvent);
		addTransition(parallelUnorderedState, parallelOrderedState, sortedEvent);
		addTransition(parallelUnorderedState, parallelUnorderedState, unorderedEvent);

		addTransition(parallelOrderedState, parallelOrderedState, parallelEvent);
		addTransition(parallelOrderedState, sequentialOrderedState, sequentialEvent);
		addTransition(parallelOrderedState, parallelOrderedState, sortedEvent);
		addTransition(parallelOrderedState, parallelUnorderedState, unorderedEvent);
	}
}
