package edu.cuny.hunter.streamrefactoring.core.analysis.rules;

import java.util.HashMap;
import java.util.Map;

import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.events.IDispatchEvent;
import com.ibm.wala.classLoader.IClass;

import edu.cuny.hunter.streamrefactoring.core.analysis.StreamExecutionMode;

public class StreamExecutionModeTypeStateRule extends StreamAttributeTypestateRule {

	private IDFAState sequentialState;

	private IDFAState parallelState;

	protected Map<IDFAState, StreamExecutionMode> dfaStateToExecutionMap;

	public StreamExecutionModeTypeStateRule(IClass streamClass) {
		super(streamClass, "execution mode");
	}

	@Override
	protected void addAutomaton() {
		// a bottom state result would need to defer to the initial stream
		// ordering, which is in the field of the stream.
		bottomState = addState(BOTTOM_STATE_NAME, true);

		sequentialState = addState(StreamExecutionMode.SEQUENTIAL);

		if (this.getDFAStateToExecutionMap() == null)
			this.setDFAStateToExecutionMap(new HashMap<>(2));

		this.getDFAStateToExecutionMap().put(this.getSequentialState(), StreamExecutionMode.SEQUENTIAL);

		parallelState = addState(StreamExecutionMode.PARALLEL);
		this.getDFAStateToExecutionMap().put(this.getParallelState(), StreamExecutionMode.PARALLEL);

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

	protected IDFAState getSequentialState() {
		return sequentialState;
	}

	protected IDFAState getParallelState() {
		return parallelState;
	}

	public StreamExecutionMode getStreamExecutionMode(IDFAState state) {
		return this.getDFAStateToExecutionMap().get(state);
	}

	protected Map<IDFAState, StreamExecutionMode> getDFAStateToExecutionMap() {
		return this.dfaStateToExecutionMap;
	}

	protected void setDFAStateToExecutionMap(HashMap<IDFAState, StreamExecutionMode> dfaStateToExecutionMap) {
		this.dfaStateToExecutionMap = dfaStateToExecutionMap;
	}
}
