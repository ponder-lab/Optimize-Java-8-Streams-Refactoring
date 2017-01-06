package edu.cuny.hunter.streamrefactoring.core.visitors;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import edu.cuny.hunter.streamrefactoring.core.descriptors.ConvertStreamToParallelRefactoringDescriptor;
import edu.cuny.hunter.streamrefactoring.core.utils.Util;

public class StreamAnalysisVisitor extends ASTVisitor {
	private Set<Stream> streamSet = new HashSet<>();

	public StreamAnalysisVisitor() {
		super();
	}

	public StreamAnalysisVisitor(boolean visitDocTags) {
		super(visitDocTags);
	}

	@SuppressWarnings("restriction")
	@Override
	public boolean visit(MethodInvocation node) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		IMethod method = (IMethod) methodBinding.getJavaElement();

		String methodIdentifier = null;
		try {
			methodIdentifier = Util.getMethodIdentifier(method);
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}

		String fullyQualifiedName = method.getDeclaringType().getFullyQualifiedName();

		if (fullyQualifiedName.equals("java.util.Collection"))
			switch (methodIdentifier) {
			case "stream()": {
				// we know it's a sequential stream.
				Stream stream = new Stream(node, StreamExecutionMode.SEQUENTIAL);
				this.getStreamSet().add(stream);
				break;
			}
			case "parallelStream()": {
				Stream stream = new Stream(node, StreamExecutionMode.PARALLEL);
				this.getStreamSet().add(stream);
			}
			} 

		return super.visit(node);
	}

	public Set<Stream> getStreamSet() {
		return streamSet;
	}

}
