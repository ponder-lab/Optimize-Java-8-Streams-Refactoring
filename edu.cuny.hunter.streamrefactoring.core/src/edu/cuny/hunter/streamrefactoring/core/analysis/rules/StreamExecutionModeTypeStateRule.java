package edu.cuny.hunter.streamrefactoring.core.analysis.rules;

import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.events.IDispatchEvent;
import com.ibm.wala.classLoader.IClass;

import edu.cuny.hunter.streamrefactoring.core.analysis.StreamExecutionMode;

public class StreamExecutionModeTypeStateRule extends StreamAttributeTypestateRule {

	private IDFAState sequentialState;
	private IDFAState parallelState;

	public StreamExecutionModeTypeStateRule(IClass streamClass) {
		super(streamClass, "execution mode");
	}

	@Override
	protected void addAutomaton() {
		// a bottom state result would need to defer to the initial stream
		// ordering, which is in the field of the stream.
		bottomState = addState(BOTTOM_STATE_NAME, true);
		sequentialState = addState(StreamExecutionMode.SEQUENTIAL);
		parallelState = addState(StreamExecutionMode.PARALLEL);

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

	public IDFAState getSequentialState() {
		return sequentialState;
	}

	public IDFAState getParallelState() {
		return parallelState;
	}
}
