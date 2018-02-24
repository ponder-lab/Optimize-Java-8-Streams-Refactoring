package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.events.IDispatchEvent;
import com.ibm.wala.classLoader.IClass;

public class StreamOrderingTypeStateRule extends StreamAttributeTypestateRule {

	protected Map<IDFAState, Ordering> dfaStateToOrderingMap;

	public StreamOrderingTypeStateRule(IClass streamClass) {
		super(streamClass, "ordering");
	}

	@Override
	protected void addAutomaton() {
		super.addAutomaton();

		IDFAState orderedState = addState(Ordering.ORDERED);
		
		if (this.getDFAStateToOrderingMap() == null)
			this.setDFAStateToOrderingMap(new HashMap<>(2));
		
		this.getDFAStateToOrderingMap().put(orderedState, Ordering.ORDERED);
		
		IDFAState unorderedState = addState(Ordering.UNORDERED);
		this.getDFAStateToOrderingMap().put(unorderedState, Ordering.UNORDERED);

		IDispatchEvent sortedEvent = addEvent("sorted", ".*sorted\\(.*\\).*");
		IDispatchEvent unorderedEvent = addEvent("unordered", ".*unordered\\(\\).*");

		// TODO: Need to add concat().
		addTransition(bottomState, orderedState, sortedEvent);
		addTransition(bottomState, unorderedState, unorderedEvent);
		addTransition(unorderedState, orderedState, sortedEvent);
		addTransition(unorderedState, unorderedState, unorderedEvent);
		addTransition(orderedState, orderedState, sortedEvent);
		addTransition(orderedState, unorderedState, unorderedEvent);
	}

	@Override
	protected void addPossibleAttributes(Stream stream, Collection<IDFAState> states) {
		super.addPossibleAttributes(stream, states);

		Set<Ordering> set = states.stream().map(this::getOrdering).collect(Collectors.toSet());
		stream.addPossibleOrderingCollection(set);
	}

	public Ordering getOrdering(IDFAState state) {
		return this.getDFAStateToOrderingMap().get(state);
	}

	protected Map<IDFAState, Ordering> getDFAStateToOrderingMap() {
		return dfaStateToOrderingMap;
	}

	protected void setDFAStateToOrderingMap(Map<IDFAState, Ordering> dfaStateToOrderingMap) {
		this.dfaStateToOrderingMap = dfaStateToOrderingMap;
	}
}
