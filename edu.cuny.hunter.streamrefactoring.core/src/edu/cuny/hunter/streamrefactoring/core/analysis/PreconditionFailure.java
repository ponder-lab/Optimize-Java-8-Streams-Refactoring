package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.util.Arrays;

public enum PreconditionFailure {
	INCONSISTENT_POSSIBLE_STREAM_SOURCE_ORDERING(1),
	NON_ITERABLE_POSSIBLE_STREAM_SOURCE(2),
	NON_INSTANTIABLE_POSSIBLE_STREAM_SOURCE(3),
	NON_DETERMINABLE_STREAM_SOURCE_ORDERING(4),
	NON_DETERMINABLE_REDUCTION_ORDERING(5),
	INCONSISTENT_POSSIBLE_EXECUTION_MODES(6),
	INCONSISTENT_POSSIBLE_ORDERINGS(7),
	HAS_SIDE_EFFECTS(8), // P1.
	HAS_SIDE_EFFECTS2(9), // P2.
	REDUCE_ORDERING_MATTERS(10), // P3.
	NO_STATEFUL_INTERMEDIATE_OPERATIONS(11), // P4 or P5.
	UNORDERED(12), // P4 or P4.
	NO_TERMINAL_OPERATIONS(13),
	CURRENTLY_NOT_HANDLED(14), // should just be #97 currently.
	STREAM_CODE_NOT_REACHABLE(15), // either pivotal code isn't reachable or
									// entry points are misconfigured.
	NO_ENTRY_POINT(16); // user didn't specify entry points.

	private int code;

	static {
		// check that the codes are unique.
		if (Arrays.stream(PreconditionFailure.values()).map(PreconditionFailure::getCode).distinct()
				.count() != PreconditionFailure.values().length)
			throw new IllegalStateException("Codes aren't unique.");
	}

	private PreconditionFailure(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static void main(String[] args) {
		System.out.println("code,name");
		for (PreconditionFailure failure : PreconditionFailure.values())
			System.out.println(failure.getCode() + "," + failure);
	}
}
