package edu.cuny.hunter.streamrefactoring.ui.tests;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import edu.cuny.hunter.streamrefactoring.core.analysis.ExecutionMode;
import edu.cuny.hunter.streamrefactoring.core.analysis.Ordering;
import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionFailure;
import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionSuccess;
import edu.cuny.hunter.streamrefactoring.core.analysis.Refactoring;
import edu.cuny.hunter.streamrefactoring.core.analysis.Stream;
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
		return expectedActions;
	}

	public String getExpectedCreation() {
		return expectedCreation;
	}

	public void evaluate(Stream stream) {
		Set<ExecutionMode> executionModes = stream.getPossibleExecutionModes();
		assertEquals(this.errorMessage("execution mode"), this.getExpectedExecutionModes(), executionModes);

		Set<Ordering> orderings = stream.getPossibleOrderings();
		assertEquals(this.errorMessage("orderings"), this.getExpectedOrderings(), orderings);

		assertEquals(this.errorMessage("side effects"), this.isExpectingSideEffects(), stream.hasPossibleSideEffects());
		assertEquals(this.errorMessage("stateful intermediate operations"),
				this.isExpectingStatefulIntermediateOperation(), stream.hasPossibleStatefulIntermediateOperations());
		assertEquals(this.errorMessage("ROM"), this.isExpectingThatReduceOrderingMatters(),
				stream.reduceOrderingPossiblyMatters());
		assertEquals(this.errorMessage("transformation actions"), this.getExpectedActions(), stream.getActions());
		assertEquals(this.errorMessage("passing precondition"), this.getExpectedPassingPrecondition(),
				stream.getPassingPrecondition());
		assertEquals(this.errorMessage("refactoring"), this.getExpectedRefactoring(), stream.getRefactoring());
		assertEquals(this.errorMessage("status severity"), this.getExpectedStatusSeverity(),
				stream.getStatus().getSeverity());

		Set<Integer> actualCodes = Arrays.stream(stream.getStatus().getEntries()).map(e -> e.getCode())
				.collect(Collectors.toSet());

		Set<Integer> expectedCodes = this.getExpectedFailures().stream().map(e -> e.getCode())
				.collect(Collectors.toSet());

		assertEquals(this.errorMessage("status codes"), expectedCodes, actualCodes);
	}

	public Set<PreconditionFailure> getExpectedFailures() {
		return expectedFailures;
	}

	public Set<Ordering> getExpectedOrderings() {
		return expectedOrderings;
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
		return expectingSideEffects;
	}

	public boolean isExpectingStatefulIntermediateOperation() {
		return expectingStatefulIntermediateOperation;
	}

	public boolean isExpectingThatReduceOrderingMatters() {
		return expectingThatReduceOrderingMatters;
	}
}
