package edu.cuny.hunter.streamrefactoring.core.visitors;

import org.eclipse.jdt.core.dom.ASTVisitor;

public class StreamAnalysisVisitor extends ASTVisitor {
	
	public StreamAnalysisVisitor() {
		super();
	}
	
	public StreamAnalysisVisitor(boolean visitDocTags) {
		super(visitDocTags);
	}

}
