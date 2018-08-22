package edu.cuny.hunter.streamrefactoring.ui.tests;

import java.util.Set;

import edu.cuny.hunter.streamrefactoring.core.analysis.ExecutionMode;
import edu.cuny.hunter.streamrefactoring.core.analysis.Ordering;
import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionFailure;
import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionSuccess;
import edu.cuny.hunter.streamrefactoring.core.analysis.Refactoring;
import edu.cuny.hunter.streamrefactoring.core.analysis.TransformationAction;

class StreamAnalysisExpectedResult {
	private Set<TransformationAction> expectedActions;

	private String expectedCreation;

	private Set<ExecutionMode> expectedExecutionModes;

	private Set<PreconditionFailure> expectedFailures;

	private Set<Ordering> expectedOrderings;

	private PreconditionSuccess expectedPassingPrecondition;

	private Refactoring expectedRefactoring;

	private int expectedStatusSeverity;

	private boolean expectingSideEffects;

	private boolean expectingStatefulIntermediateOperation;

	private boolean expectingThatReduceOrderingMatters;

	public StreamAnalysisExpectedResult(String expectedCreation, Set<ExecutionMode> expectedExecutionModes,
			Set<Ordering> expectedOrderings, boolean expectingSideEffects,
			boolean expectingStatefulIntermediateOperation, boolean expectingThatReduceOrderingMatters,
			Set<TransformationAction> expectedActions, PreconditionSuccess expectedPassingPrecondition,
			Refactoring expectedRefactoring, int expectedStatusSeverity, Set<PreconditionFailure> expectedFailures) {
		this.expectedCreation = expectedCreation;
		this.expectedExecutionModes = expectedExecutionModes;
		this.expectedOrderings = expectedOrderings;
		this.expectingSideEffects = expectingSideEffects;
		this.expectingStatefulIntermediateOperation = expectingStatefulIntermediateOperation;
		this.expectingThatReduceOrderingMatters = expectingThatReduceOrderingMatters;
		this.expectedActions = expectedActions;
		this.expectedPassingPrecondition = expectedPassingPrecondition;
		this.expectedRefactoring = expectedRefactoring;
		this.expectedStatusSeverity = expectedStatusSeverity;
		this.expectedFailures = expectedFailures;
	}

	public Set<TransformationAction> getExpectedActions() {
		return this.expectedActions;
	}

	public String getExpectedCreation() {
		return this.expectedCreation;
	}

	public Set<ExecutionMode> getExpectedExecutionModes() {
		return this.expectedExecutionModes;
	}

	public Set<PreconditionFailure> getExpectedFailures() {
		return this.expectedFailures;
	}

	public Set<Ordering> getExpectedOrderings() {
		return this.expectedOrderings;
	}

	public PreconditionSuccess getExpectedPassingPrecondition() {
		return this.expectedPassingPrecondition;
	}

	public Refactoring getExpectedRefactoring() {
		return this.expectedRefactoring;
	}

	public int getExpectedStatusSeverity() {
		return this.expectedStatusSeverity;
	}

	public boolean isExpectingSideEffects() {
		return this.expectingSideEffects;
	}

	public boolean isExpectingStatefulIntermediateOperation() {
		return this.expectingStatefulIntermediateOperation;
	}

	public boolean isExpectingThatReduceOrderingMatters() {
		return this.expectingThatReduceOrderingMatters;
	}
}
