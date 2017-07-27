package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.events.IDispatchEvent;
import com.ibm.wala.classLoader.IClass;

public class StreamExecutionModeTypeStateRule extends StreamAttributeTypestateRule {

	protected Map<IDFAState, ExecutionMode> dfaStateToExecutionMap;

	public StreamExecutionModeTypeStateRule(IClass streamClass) {
		super(streamClass, "execution mode");
	}

	@Override
	protected void addAutomaton() {
		super.addAutomaton();

		IDFAState sequentialState = addState(ExecutionMode.SEQUENTIAL);
		
		if (this.getDFAStateToExecutionMap() == null)
			this.setDFAStateToExecutionMap(new HashMap<>(2));
		
		this.getDFAStateToExecutionMap().put(sequentialState, ExecutionMode.SEQUENTIAL);

		IDFAState parallelState = addState(ExecutionMode.PARALLEL);
		this.getDFAStateToExecutionMap().put(parallelState, ExecutionMode.PARALLEL);

		IDispatchEvent parallelEvent = addEvent("parallel", ".*parallel\\(\\).*");
		IDispatchEvent sequentialEvent = addEvent("sequential", ".*sequential\\(\\).*");

		// TODO: Need to add concat().
		addTransition(bottomState, parallelState, parallelEvent);
		addTransition(bottomState, sequentialState, sequentialEvent);
		addTransition(sequentialState, parallelState, parallelEvent);
		addTransition(sequentialState, sequentialState, sequentialEvent);
		addTransition(parallelState, sequentialState, sequentialEvent);
		addTransition(parallelState, parallelState, parallelEvent);
	}

	protected ExecutionMode getStreamExecutionMode(IDFAState state) {
		return this.getDFAStateToExecutionMap().get(state);
	}

	protected Map<IDFAState, ExecutionMode> getDFAStateToExecutionMap() {
		return this.dfaStateToExecutionMap;
	}

	protected void setDFAStateToExecutionMap(HashMap<IDFAState, ExecutionMode> dfaStateToExecutionMap) {
		this.dfaStateToExecutionMap = dfaStateToExecutionMap;
	}

	@Override
	protected void addPossibleAttributes(Stream stream, Collection<IDFAState> states) {
		super.addPossibleAttributes(stream, states);

		Set<ExecutionMode> set = states.stream().map(this::getStreamExecutionMode).collect(Collectors.toSet());
		stream.addPossibleExecutionModeCollection(set);
	}
}
