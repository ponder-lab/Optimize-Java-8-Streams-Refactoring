package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
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
import org.eclipse.jdt.internal.ui.preferences.JavaEditorColoringPreferencePage;

import com.ibm.wala.ide.util.JavaEclipseProjectPath;
import com.ibm.wala.ide.util.JdtUtil;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.cast.java.client.JDTJavaSourceAnalysisEngine;
import com.ibm.wala.client.AbstractAnalysisEngine;
import com.ibm.wala.ide.util.EclipseProjectPath.AnalysisScopeType;

import edu.cuny.hunter.streamrefactoring.core.descriptors.ConvertStreamToParallelRefactoringDescriptor;
import edu.cuny.hunter.streamrefactoring.core.utils.Util;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;
import edu.cuny.hunter.streamrefactoring.core.wala.WalaUtil;

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
			try {
				inferStreamOrdering(stream, node);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			this.getStreamSet().add(stream);
		}

		return super.visit(node);
	}

	private void inferStreamOrdering(Stream stream, MethodInvocation node)
			throws IOException, CoreException, ClassHierarchyException {
		ITypeBinding expressionTypeBinding = node.getExpression().resolveTypeBinding();
		String expressionTypeQualifiedName = expressionTypeBinding.getErasure().getQualifiedName();
		IMethodBinding methodBinding = node.resolveMethodBinding();

		if (JdtFlags.isStatic(methodBinding)) {
			// static methods returning unordered streams.
			if (expressionTypeQualifiedName.equals("java.util.stream.Stream")) {
				String methodIdentifier = getMethodIdentifier(methodBinding);
				if (methodIdentifier.equals("generate(java.util.function.Supplier)"))
					stream.setOrdering(StreamOrdering.UNORDERED);
			} else
				stream.setOrdering(StreamOrdering.ORDERED);
		} else { // instance method.
			IJavaElement javaElement = methodBinding.getJavaElement();
			IJavaProject javaProject = javaElement.getJavaProject();
			AbstractAnalysisEngine engine = new EclipseProjectAnalysisEngine(javaProject);
			// FIXME: [RK] Inefficient to build this every time, I'd imagine.
			engine.buildAnalysisScope();
			IClassHierarchy cha = engine.buildClassHierarchy();
			System.out.println("# of classes :" + cha.getNumberOfClasses());

			// JDTJavaSourceAnalysisEngine engine = new
			// JDTJavaSourceAnalysisEngine(
			// node.resolveMethodBinding().getJavaElement().getJavaProject());
			// engine.setDump(true);
			// engine.buildAnalysisScope();
			// engine.buildClassHierarchy();
			// IClassHierarchy cha = engine.getClassHierarchy();

			// JavaEclipseProjectPath path = JavaEclipseProjectPath.make(
			// javaProject,
			// AnalysisScopeType.SOURCE_FOR_PROJ_AND_LINKED_PROJS);

			// AnalysisScope scope =
			// WalaUtil.mergeProjectPaths(Collections.singleton(path));
			// ClassHierarchy cha = ClassHierarchy.make(scope);

			// FIXME: What if there is something under this that is ordered?
			if (expressionTypeQualifiedName.equals("java.util.HashSet"))
				stream.setOrdering(StreamOrdering.UNORDERED);
			else
				// FIXME: A java.util.Set may actually not be ordered.
				stream.setOrdering(StreamOrdering.ORDERED);
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
