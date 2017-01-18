package edu.cuny.hunter.streamrefactoring.core.analysis;

import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * An abstract notion of a stream in memory.
 * 
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi
 *         Khatchadourian</a>
 *
 */
public class Stream {
	private MethodInvocation streamCreation;

	private StreamExecutionMode executionMode;

	private StreamOrdering ordering;

	private RefactoringStatus status;

	public Stream(MethodInvocation streamCreation) {
		super();
		this.streamCreation = streamCreation;
	}

	public Stream(MethodInvocation invocation, StreamExecutionMode executionMode) {
		this.streamCreation = invocation;
		this.executionMode = executionMode;
	}

	public Stream() {
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Stream [streamCreation=");
		builder.append(streamCreation);
		builder.append(", executionMode=");
		builder.append(executionMode);
		builder.append(", ordering=");
		builder.append(ordering);
		builder.append(", status=");
		builder.append(status);
		builder.append("]");
		return builder.toString();
	}

	public StreamExecutionMode getExecutionMode() {
		return executionMode;
	}

	public void setExecutionMode(StreamExecutionMode executionMode) {
		this.executionMode = executionMode;
	}

	public StreamOrdering getOrdering() {
		return ordering;
	}

	public void setOrdering(StreamOrdering ordering) {
		this.ordering = ordering;
	}

	public MethodInvocation getStreamCreation() {
		return streamCreation;
	}

	public RefactoringStatus getStatus() {
		return status;
	}
}
