package edu.cuny.hunter.streamrefactoring.core.analysis;

public enum TransformationAction {
	CONVERT_TO_PARALLEL,
	UNORDER,
	CONVERT_TO_SEQUENTIAL,
	CONVERT_COLLECTOR_TO_CONCURRENT,
	CONVERT_COLLECTOR_TO_NON_CONCURRENT
}
