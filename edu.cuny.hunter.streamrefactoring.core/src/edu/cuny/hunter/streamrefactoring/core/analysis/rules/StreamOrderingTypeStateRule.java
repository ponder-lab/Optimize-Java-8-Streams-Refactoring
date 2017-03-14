package edu.cuny.hunter.streamrefactoring.core.analysis.rules;

import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.events.IDispatchEvent;
import com.ibm.wala.classLoader.IClass;

public class StreamOrderingTypeStateRule extends StreamAttributeTypestateRule {

	public StreamOrderingTypeStateRule(IClass streamClass) {
		super(streamClass, "ordering");
	}

	@Override
	protected void addAutomaton() {
		// a bottom state result would need to defer to the initial stream
		// ordering, which is in the field of the stream.
		IDFAState bottomState = addState("bottom", true);
		IDFAState orderedState = addState("ordered");
		IDFAState unorderedState = addState("unordered");

		IDispatchEvent sortedEvent = addEvent("sorted", ".*sorted\\(\\).*");
		IDispatchEvent unorderedEvent = addEvent("unordered", ".*unordered\\(\\).*");
		
		// TODO: Need to add concat().
		addTransition(bottomState, orderedState, sortedEvent);
		addTransition(bottomState, unorderedState, unorderedEvent);
		addTransition(unorderedState, orderedState, sortedEvent);
		addTransition(unorderedState, unorderedState, unorderedEvent);
		addTransition(orderedState, orderedState, sortedEvent);
		addTransition(orderedState, unorderedState, unorderedEvent);
	}

}
