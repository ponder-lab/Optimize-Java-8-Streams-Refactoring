package edu.cuny.hunter.streamrefactoring.core.utils;

import static org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester.getTopLevelType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import edu.cuny.hunter.streamrefactoring.core.refactorings.MigrateSkeletalImplementationToInterfaceRefactoringProcessor;

/**
 * @author <a href="mailto:rkhatchadourian@citytech.cuny.edu">Raffi
 *         Khatchadourian</a>
 * @see org.eclipse.jdt.internal.corext.refactoring.
 *      RefactoringAvailabilityTester
 */
@SuppressWarnings("restriction")
public final class RefactoringAvailabilityTester {

	private RefactoringAvailabilityTester() {
	}

	public static boolean isInterfaceMigrationAvailable(IMethod method, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		return isInterfaceMigrationAvailable(method, true, monitor);
	}

	public static boolean isInterfaceMigrationAvailable(IMethod method, boolean allowConcreteClasses,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		if (!Checks.isAvailable(method))
			return false;
		if (method.isConstructor())
			return false;
		if (JdtFlags.isNative(method))
			return false;
		if (JdtFlags.isStatic(method))
			return false;
		if (JdtFlags.isAbstract(method))
			return false;
		// TODO: Should this be here?
		if (JdtFlags.isSynchronized(method))
			return false;
		// TODO: Should this be here?
		if (JdtFlags.isFinal(method))
			return false;
		if (method.getResource().isDerived(IResource.CHECK_ANCESTORS))
			return false;

		final IType declaring = method.getDeclaringType();
		if (declaring != null) {
			if (declaring.isInterface())
				return false; // Method is already in an interface
			else if (!allowConcreteClasses && !Flags.isAbstract(declaring.getFlags()))
				return false; // no concrete types allowed.
		}

		// ensure that there is a target method.
		IMethod targetMethod = MigrateSkeletalImplementationToInterfaceRefactoringProcessor.getTargetMethod(method,
				monitor);
		if (targetMethod == null) // no possible target.
			return false;

		return true;
	}

	public static boolean isInterfaceMigrationAvailable(IMethod[] methods, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		if (methods != null && methods.length != 0) {
			// FIXME: This seems wrong. There should be a look up here and use
			// getDeclaringType() on each method.
			final IType type = getTopLevelType(methods);

			if (type != null && getMigratableSkeletalImplementations(type, monitor).length != 0)
				return true;

			for (int index = 0; index < methods.length; index++)
				if (!isInterfaceMigrationAvailable(methods[index], monitor))
					return false;

			// return isCommonDeclaringType(methods);
		}
		return false;
	}

	public static IMethod[] getMigratableSkeletalImplementations(final IType type, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		List<IMethod> ret = new ArrayList<>();

		if (type.exists()) {
			IMethod[] methodsOfType = type.getMethods();
			for (int i = 0; i < methodsOfType.length; i++) {
				IMethod method = methodsOfType[i];
				if (RefactoringAvailabilityTester.isInterfaceMigrationAvailable(method, monitor))
					ret.add(method);
			}
		}

		return ret.toArray(new IMethod[ret.size()]);
	}

}
