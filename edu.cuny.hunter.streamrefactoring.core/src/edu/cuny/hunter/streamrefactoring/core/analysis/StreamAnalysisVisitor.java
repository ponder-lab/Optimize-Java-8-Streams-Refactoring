package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import edu.cuny.hunter.streamrefactoring.core.descriptors.ConvertStreamToParallelRefactoringDescriptor;
import edu.cuny.hunter.streamrefactoring.core.utils.Util;

@SuppressWarnings("restriction")
public class StreamAnalysisVisitor extends ASTVisitor {
	private Set<Stream> streamSet = new HashSet<>();

	public StreamAnalysisVisitor() {
		super();
	}

	public StreamAnalysisVisitor(boolean visitDocTags) {
		super(visitDocTags);
	}

	@Override
	public boolean visit(MethodInvocation node) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		ITypeBinding returnType = methodBinding.getReturnType();
		boolean returnTypeImplementsBaseStream = implementsBaseStream(returnType);

		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		boolean declaringClassImplementsBaseStream = implementsBaseStream(declaringClass);

		// java.util.stream.BaseStream is the top-level interface for all
		// streams. Make sure we don't include intermediate operations.
		if (returnTypeImplementsBaseStream
				&& !(!JdtFlags.isStatic(methodBinding) && declaringClassImplementsBaseStream)) {
			Stream stream = new Stream(node);
			inferStreamExecution(stream, node);
			inferStreamOrdering(stream, node);

			this.getStreamSet().add(stream);
		}

		return super.visit(node);
	}

	private void inferStreamOrdering(Stream stream, MethodInvocation node) {
		ITypeBinding expressionTypeBinding = node.getExpression().resolveTypeBinding();
		String expressionTypeQualifiedName = expressionTypeBinding.getErasure().getQualifiedName();

		if (JdtFlags.isStatic(node.resolveMethodBinding())) {
			// static methods returning unordered streams.
			if (expressionTypeQualifiedName.equals("java.util.stream.Stream")) {
				String methodIdentifier = getMethodIdentifier(node.resolveMethodBinding());
				if (methodIdentifier.equals("generate(java.util.function.Supplier)"))
					stream.setOrdering(StreamOrdering.UNORDERED);
			} else
				stream.setOrdering(StreamOrdering.ORDERED);
		} else { // instance method.
			if (expressionTypeQualifiedName.equals("java.util.HashSet")) // FIXME: What if there is something under this that is ordered?
				stream.setOrdering(StreamOrdering.UNORDERED);
			else
				stream.setOrdering(StreamOrdering.ORDERED); // FIXME: A java.util.Set may actually not be ordered.
		}

		// TODO: Are there more such methods? TreeSet?
		// FIXME: Can we have a better approximation of the expression run time
		// type?

		System.out.println(expressionTypeQualifiedName);
	}

	private void inferStreamExecution(Stream stream, MethodInvocation node) {
		String methodIdentifier = getMethodIdentifier(node.resolveMethodBinding());

		if (methodIdentifier.equals("parallelStream()"))
			stream.setExecutionMode(StreamExecutionMode.PARALLEL);
		else
			stream.setExecutionMode(StreamExecutionMode.SEQUENTIAL);
	}

	private String getMethodIdentifier(IMethodBinding methodBinding) {
		IMethod method = (IMethod) methodBinding.getJavaElement();

		String methodIdentifier = null;
		try {
			methodIdentifier = Util.getMethodIdentifier(method);
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
		return methodIdentifier;
	}

	private static boolean implementsBaseStream(ITypeBinding type) {
		Set<ITypeBinding> implementedInterfaces = getImplementedInterfaces(type);
		return implementedInterfaces.stream()
				.anyMatch(i -> i.getErasure().getQualifiedName().equals("java.util.stream.BaseStream"));
	}

	private static Set<ITypeBinding> getImplementedInterfaces(ITypeBinding type) {
		Set<ITypeBinding> ret = new HashSet<>();

		if (type.isInterface())
			ret.add(type);

		ret.addAll(getAllInterfaces(type));
		return ret;
	}

	private static Set<ITypeBinding> getAllInterfaces(ITypeBinding type) {
		Set<ITypeBinding> ret = new HashSet<>();
		ITypeBinding[] interfaces = type.getInterfaces();
		ret.addAll(Arrays.asList(interfaces));

		for (ITypeBinding interfaceBinding : interfaces)
			ret.addAll(getAllInterfaces(interfaceBinding));

		return ret;
	}

	public Set<Stream> getStreamSet() {
		return streamSet;
	}

}
