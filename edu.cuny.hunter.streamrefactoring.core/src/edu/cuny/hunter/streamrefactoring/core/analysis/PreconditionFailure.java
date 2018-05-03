package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.util.Arrays;

public enum PreconditionFailure {
	CURRENTLY_NOT_HANDLED(14),
	HAS_SIDE_EFFECTS(8),
	HAS_SIDE_EFFECTS2(9),
	INCONSISTENT_POSSIBLE_EXECUTION_MODES(6),
	INCONSISTENT_POSSIBLE_ORDERINGS(7),
	INCONSISTENT_POSSIBLE_STREAM_SOURCE_ORDERING(1),
	NO_APPLICATION_CODE_IN_CALL_STRINGS(17),
	// entry points are misconfigured.
	NO_ENTRY_POINT(16), // P1.
	NO_STATEFUL_INTERMEDIATE_OPERATIONS(11), // P2.
	NO_TERMINAL_OPERATIONS(13), // P3.
	NON_DETERMINABLE_REDUCTION_ORDERING(5), // P4 or P5.
	NON_DETERMINABLE_STREAM_SOURCE_ORDERING(4), // P4 or P4.
	NON_INSTANTIABLE_POSSIBLE_STREAM_SOURCE(3),
	NON_ITERABLE_POSSIBLE_STREAM_SOURCE(2), // should just be #97 currently.
	REDUCE_ORDERING_MATTERS(10), // either pivotal code isn't reachable or
	STREAM_CODE_NOT_REACHABLE(15), // user didn't specify entry points.
	UNORDERED(12); // N may be too small.

	static {
		// check that the codes are unique.
		if (Arrays.stream(PreconditionFailure.values()).map(PreconditionFailure::getCode).distinct()
				.count() != PreconditionFailure.values().length)
			throw new IllegalStateException("Codes aren't unique.");
	}

	public static void main(String[] args) {
		System.out.println("code,name");
		for (PreconditionFailure failure : PreconditionFailure.values())
			System.out.println(failure.getCode() + "," + failure);
	}

	private int code;

	private PreconditionFailure(int code) {
		this.code = code;
	}

	public int getCode() {
		return this.code;
	}
}
