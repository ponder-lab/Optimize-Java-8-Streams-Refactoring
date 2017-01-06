package edu.cuny.hunter.streamrefactoring.core.visitors;

import org.eclipse.jdt.core.dom.MethodInvocation;

public class Stream {
	MethodInvocation streamCreation;

	StreamExecutionMode executionMode;

	StreamOrdering ordering;

	public Stream(MethodInvocation invocation, StreamExecutionMode executionMode) {
		this.streamCreation = invocation;
		this.executionMode = executionMode;
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
		builder.append("]");
		return builder.toString();
	}
}
