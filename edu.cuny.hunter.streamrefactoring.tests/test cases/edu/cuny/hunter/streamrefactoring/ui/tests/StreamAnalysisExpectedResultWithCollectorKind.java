package edu.cuny.hunter.streamrefactoring.ui.tests;

import static org.junit.Assert.assertEquals;

import java.util.Set;

import edu.cuny.hunter.streamrefactoring.core.analysis.CollectorKind;
import edu.cuny.hunter.streamrefactoring.core.analysis.ExecutionMode;
import edu.cuny.hunter.streamrefactoring.core.analysis.Ordering;
import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionFailure;
import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionSuccess;
import edu.cuny.hunter.streamrefactoring.core.analysis.Refactoring;
import edu.cuny.hunter.streamrefactoring.core.analysis.Stream;
import edu.cuny.hunter.streamrefactoring.core.analysis.TransformationAction;

public class StreamAnalysisExpectedResultWithCollectorKind extends StreamAnalysisExpectedResult {

	private CollectorKind expectedCollectorKind;

	public StreamAnalysisExpectedResultWithCollectorKind(String expectedCreation,
			Set<ExecutionMode> expectedExecutionModes, Set<Ordering> expectedOrderings,
			CollectorKind expectedCollectorKind, boolean expectingSideEffects,
			boolean expectingStatefulIntermediateOperation, boolean expectingThatReduceOrderingMatters,
			Set<TransformationAction> expectedActions, PreconditionSuccess expectedPassingPrecondition,
			Refactoring expectedRefactoring, int expectedStatusSeverity, Set<PreconditionFailure> expectedFailures) {
		super(expectedCreation, expectedExecutionModes, expectedOrderings, expectingSideEffects,
				expectingStatefulIntermediateOperation, expectingThatReduceOrderingMatters, expectedActions,
				expectedPassingPrecondition, expectedRefactoring, expectedStatusSeverity, expectedFailures);
		this.expectedCollectorKind = expectedCollectorKind;
	}

	@Override
	public void evaluate(Stream stream) {
		super.evaluate(stream);

		CollectorKind collectorKind = stream.getCollectorKind();
		assertEquals(this.errorMessage("collector kind"), this.getExpectedCollectorKind(), collectorKind);
	}

	public CollectorKind getExpectedCollectorKind() {
		return this.expectedCollectorKind;
	}
}
