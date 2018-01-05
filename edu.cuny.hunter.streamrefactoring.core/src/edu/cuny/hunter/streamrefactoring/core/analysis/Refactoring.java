package edu.cuny.hunter.streamrefactoring.core.analysis;

/**
 * Possible refactorings included in this plug-in.
 * 
 * @author <a href="mailto:rk1424@hunter.cuny.edu">Raffi Khatchadourian</a>
 */
public enum Refactoring {
	CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL,
	OPTIMIZE_PARALLEL_STREAM,
	OPTIMIZE_COMPLEX_MUTABLE_REDUCTION
}
