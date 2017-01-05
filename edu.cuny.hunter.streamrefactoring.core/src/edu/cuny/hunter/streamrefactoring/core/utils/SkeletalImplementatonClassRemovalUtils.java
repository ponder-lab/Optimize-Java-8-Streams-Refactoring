package edu.cuny.hunter.streamrefactoring.core.utils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;

import edu.cuny.hunter.streamrefactoring.core.refactorings.ConvertToParallelStreamRefactoringProcessor;

@SuppressWarnings("restriction")
public final class SkeletalImplementatonClassRemovalUtils {

	private static final class SuperReferenceFinder extends ASTVisitor {
		private boolean encounteredSuper;

		private SuperReferenceFinder(boolean visitDocTags) {
			super(visitDocTags);
		}

		@Override
		public boolean visit(SuperConstructorInvocation node) {
			encounteredSuper = true;
			return false;
		}

		@Override
		public boolean visit(SuperFieldAccess node) {
			encounteredSuper = true;
			return false;
		}

		@Override
		public boolean visit(SuperMethodInvocation node) {
			encounteredSuper = true;
			return false;
		}

		@Override
		public boolean visit(SuperMethodReference node) {
			encounteredSuper = true;
			return false;
		}

		public boolean hasEncounteredSuper() {
			return encounteredSuper;
		}
	}

	public static RefactoringStatus checkRemoval(IMethod sourceMethod, IType destinationInterface, IType typeToRemove,
			Set<IMethod> methodsToMigrate, Optional<IProgressMonitor> monitor) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();
		monitor.ifPresent(m -> m.beginTask("Checking if type can be removed ...", IProgressMonitor.UNKNOWN));
		try {
			// TODO: Add code to also remove concrete types #25.
			if (!JdtFlags.isAbstract(typeToRemove))
				status.addError("For now, can only remove abstact skeletal implementation classes.",
						new RefactoringStatusContext() {

							@Override
							public Object getCorrespondingElement() {
								return typeToRemove;
							}
						});

			if (!willBeEmpty(typeToRemove, methodsToMigrate))
				status.addError("The skeletal implementation class is not empty after migrating methods.",
						new RefactoringStatusContext() {

							@Override
							public Object getCorrespondingElement() {
								return typeToRemove;
							}
						});

			if (!superClassImplementsDestinationInterface(typeToRemove, destinationInterface,
					monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN))))
				status.addError(
						"The skeletal implementation class has a superclass that does not implement the destination interface.",
						new RefactoringStatusContext() {

							@Override
							public Object getCorrespondingElement() {
								try {
									return typeToRemove.newSupertypeHierarchy(new NullProgressMonitor())
											.getSuperclass(typeToRemove);
								} catch (JavaModelException e) {
									throw new RuntimeException(e);
								}
							}
						});

			// if the type to remove doesn't have a superclass and its subtypes
			// contain super references.
			if (typeToRemove.getSuperclassName() == null && subclassesContainSuperReferences(sourceMethod, typeToRemove,
					monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN))))
				status.addError(
						"The skeletal implementation class has no superclass but its subtypes contain references to super.",
						new RefactoringStatusContext() {

							@Override
							public Object getCorrespondingElement() {
								return typeToRemove;
							}
						});
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
		return status;
	}

	/**
	 * Returns true if the given type will be empty given a set of methods to
	 * remove from the type and false otherwise.
	 * 
	 * @param type
	 *            The type to check for emptiness.
	 * @param methodsToRemove
	 *            The methods that will be removed from the given type.
	 * @return True if the given type will be empty as a result of removing the
	 *         given methods from the type.
	 * @throws JavaModelException
	 *             On a java model problem.
	 */
	private static boolean willBeEmpty(IType type, Set<IMethod> methodsToRemove) throws JavaModelException {
		IMethod[] methods = type.getMethods();
		Set<IMethod> methodsRemaining = new LinkedHashSet<>(Arrays.asList(methods));
		methodsRemaining.removeAll(methodsToRemove);

		return methodsRemaining.isEmpty() && type.getFields().length == 0 && type.getInitializers().length == 0
				&& type.getTypes().length == 0;
	}

	private static boolean superClassImplementsDestinationInterface(IType type, IType destinationInterface,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		monitor.ifPresent(m -> m.beginTask("Checking superclass ...", IProgressMonitor.UNKNOWN));
		try {
			if (type.getSuperclassName() != null) { // there's a superclass.
				ITypeHierarchy superTypeHierarchy = ConvertToParallelStreamRefactoringProcessor
						.getSuperTypeHierarchy(type,
								monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN)));
				IType superclass = superTypeHierarchy.getSuperclass(type);
				return Arrays.stream(superTypeHierarchy.getAllSuperInterfaces(superclass))
						.anyMatch(i -> i.equals(destinationInterface));
			}
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}

		return true; // vacuously true since there's no superclass.
	}

	private static boolean subclassesContainSuperReferences(IMethod sourceMethod, IType type,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		monitor.ifPresent(m -> m.beginTask("Checking for super references ...", IProgressMonitor.UNKNOWN));
		try {
			IType[] subclasses = type.newTypeHierarchy(
					new SubProgressMonitor(monitor.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN))
					.getSubclasses(type);

			for (IType subclass : subclasses) {
				IMethod[] methods = subclass.findMethods(sourceMethod);
				if (methods != null) {
					for (IMethod method : methods) {
						CompilationUnit unit = getCompilationUnit(method.getTypeRoot(),
								monitor.map(m -> (IProgressMonitor) new SubProgressMonitor(m, IProgressMonitor.UNKNOWN))
										.orElseGet(NullProgressMonitor::new));

						SuperReferenceFinder finder = new SuperReferenceFinder(false);
						unit.accept(finder);

						if (finder.hasEncounteredSuper())
							return true;
					}
				}
			}
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
		return false;
	}

	private static CompilationUnit getCompilationUnit(ITypeRoot typeRoot, IProgressMonitor monitor) {
		return RefactoringASTParser.parseWithASTProvider(typeRoot, true, monitor);
	}

	private SkeletalImplementatonClassRemovalUtils() {
	}
}