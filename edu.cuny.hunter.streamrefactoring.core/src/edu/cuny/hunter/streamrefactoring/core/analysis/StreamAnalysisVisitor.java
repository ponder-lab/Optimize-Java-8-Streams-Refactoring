package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.util.CancelException;

@SuppressWarnings("restriction")
public class StreamAnalysisVisitor extends ASTVisitor {
	private Set<Stream> streamSet = new HashSet<>();

	private static final Logger logger = Logger.getLogger("edu.cuny.hunter.streamrefactoring");

	public StreamAnalysisVisitor() {
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
			Stream stream = null;
			try {
				stream = new Stream(node);
			} catch (ClassHierarchyException | IOException | CoreException | InvalidClassFileException | CancelException e) {
				logger.log(Level.SEVERE, "Encountered exception while processing: " + node, e);
				throw new RuntimeException(e);
			} 
			this.getStreamSet().add(stream);
			
			stream.check();
		}

		return super.visit(node);
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
