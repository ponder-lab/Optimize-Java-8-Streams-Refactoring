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

	public void evaluate(Stream stream) {
		Set<ExecutionMode> executionModes = stream.getPossibleExecutionModes();
		assertEquals(errorMessage("execution mode"), this.getExpectedExecutionModes(), executionModes);

		Set<Ordering> orderings = stream.getPossibleOrderings();
		assertEquals(errorMessage("orderings"), this.getExpectedOrderings(), orderings);

		assertEquals(errorMessage("side effects"), isExpectingSideEffects(), stream.hasPossibleSideEffects());
		assertEquals(errorMessage("stateful intermediate operations"), isExpectingStatefulIntermediateOperation(),
				stream.hasPossibleStatefulIntermediateOperations());
		assertEquals(errorMessage("ROM"), isExpectingThatReduceOrderingMatters(),
				stream.reduceOrderingPossiblyMatters());
		assertEquals(errorMessage("transformation actions"), getExpectedActions(), stream.getActions());
		assertEquals(errorMessage("passing precondition"), getExpectedPassingPrecondition(),
				stream.getPassingPrecondition());
		assertEquals(errorMessage("refactoring"), getExpectedRefactoring(), stream.getRefactoring());
		assertEquals(errorMessage("status severity"), getExpectedStatusSeverity(), stream.getStatus().getSeverity());

		Set<Integer> actualCodes = Arrays.stream(stream.getStatus().getEntries()).map(e -> e.getCode())
				.collect(Collectors.toSet());

		Set<Integer> expectedCodes = getExpectedFailures().stream().map(e -> e.getCode()).collect(Collectors.toSet());

		assertEquals(errorMessage("status codes"), expectedCodes, actualCodes);
	}

	protected String errorMessage(String attribute) {
		return "Unexpected " + attribute + " for " + this.getExpectedCreation() + ".";
	}
}
