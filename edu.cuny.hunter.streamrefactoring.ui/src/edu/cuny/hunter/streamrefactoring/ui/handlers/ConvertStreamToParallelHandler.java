package edu.cuny.hunter.streamrefactoring.ui.handlers;

import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.osgi.framework.FrameworkUtil;

import edu.cuny.hunter.streamrefactoring.core.utils.RefactoringAvailabilityTester;
import edu.cuny.hunter.streamrefactoring.ui.wizards.MigrateSkeletalImplementationToInterfaceRefactoringWizard;

public class ConvertStreamToParallelHandler extends AbstractHandler {

	/**
	 * Gather all the streams from the user's selection.
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Optional<IProgressMonitor> monitor = Optional.empty();
		ISelection currentSelection = HandlerUtil.getCurrentSelectionChecked(event);
		List<?> list = SelectionUtil.toList(currentSelection);

		if (list != null) {
			try {
				Set<IMethod> methodSet = new HashSet<>();

				for (Object obj : list) {
					if (obj instanceof IJavaElement) {
						IJavaElement jElem = (IJavaElement) obj;
						switch (jElem.getElementType()) {
						case IJavaElement.METHOD:
							// if a method is explicitly selected, add it
							// straight away.
							methodSet.add((IMethod) jElem);
							break;
						case IJavaElement.TYPE:
							// A type is either a class, interface, or enum. Get
							// only methods from classes. TODO: Should mention
							// this in paper as a filtered context.
							methodSet.addAll(extractMethodsFromClass((IType) jElem, monitor));
							break;
						case IJavaElement.COMPILATION_UNIT:
							methodSet.addAll(extractMethodsFromCompilationUnit((ICompilationUnit) jElem, monitor));
							break;
						case IJavaElement.PACKAGE_FRAGMENT:
							methodSet.addAll(extractMethodsFromPackageFragment((IPackageFragment) jElem, monitor));
							break;
						case IJavaElement.PACKAGE_FRAGMENT_ROOT:
							methodSet.addAll(
									extractMethodsFromPackageFragmentRoot((IPackageFragmentRoot) jElem, monitor));
							break;
						case IJavaElement.JAVA_PROJECT:
							methodSet.addAll(extractMethodsFromJavaProject((IJavaProject) jElem, monitor));
							break;
						}
					}
				}

				Shell shell = HandlerUtil.getActiveShellChecked(event);
				MigrateSkeletalImplementationToInterfaceRefactoringWizard
						.startRefactoring(methodSet.toArray(new IMethod[methodSet.size()]), shell, Optional.empty());
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
				throw new ExecutionException("Failed to start refactoring", e);
			}
		}
		// TODO: What do we do if there was no input? Do we display some
		// message?
		return null;
	}

	private Set<IMethod> extractMethodsFromJavaProject(IJavaProject jProj, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();

		IPackageFragmentRoot[] roots = jProj.getPackageFragmentRoots();
		for (IPackageFragmentRoot iPackageFragmentRoot : roots)
			methodSet.addAll(extractMethodsFromPackageFragmentRoot(iPackageFragmentRoot, monitor));

		return methodSet;
	}

	private Set<IMethod> extractMethodsFromPackageFragmentRoot(IPackageFragmentRoot root,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();

		IJavaElement[] children = root.getChildren();
		for (IJavaElement child : children)
			if (child.getElementType() == IJavaElement.PACKAGE_FRAGMENT)
				methodSet.addAll(extractMethodsFromPackageFragment((IPackageFragment) child, monitor));

		return methodSet;
	}

	private Set<IMethod> extractMethodsFromPackageFragment(IPackageFragment frag, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();
		ICompilationUnit[] units = frag.getCompilationUnits();

		for (ICompilationUnit iCompilationUnit : units)
			methodSet.addAll(extractMethodsFromCompilationUnit(iCompilationUnit, monitor));

		return methodSet;
	}

	private Set<IMethod> extractMethodsFromCompilationUnit(ICompilationUnit cu, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();
		IType[] types = cu.getTypes();

		for (IType iType : types)
			methodSet.addAll(extractMethodsFromClass(iType, monitor));

		return methodSet;
	}

	private Set<IMethod> extractMethodsFromClass(IType type, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();

		if (type.isClass()) {
			for (IMethod method : type.getMethods())
//				if (RefactoringAvailabilityTester.isInterfaceMigrationAvailable(method, monitor)) {
				if (true) {
					logPossiblyMigratableMethod(method);
					methodSet.add(method);
				} else
					logNonMigratableMethod(method);

		}

		return methodSet;
	}

	private void logPossiblyMigratableMethod(IMethod method) {
		logMethod(method, "Method: %s is possibly migratable.");
	}

	private void logNonMigratableMethod(IMethod method) {
		logMethod(method, "Method: %s is not migratable.");
	}

	private void logMethod(IMethod method, String format) {
		Formatter formatter = new Formatter();

		formatter.format(format, JavaElementLabels.getElementLabel(method, JavaElementLabels.ALL_FULLY_QUALIFIED));

		JavaPlugin.log(new Status(IStatus.INFO, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
				formatter.toString()));

		formatter.close();
	}
}