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

import edu.cuny.hunter.streamrefactoring.ui.wizards.ConvertStreamToParallelRefactoringWizard;

public class ConvertStreamToParallelHandler extends AbstractHandler {

	/**
	 * Gather all the streams from the user's selection.
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Optional<IProgressMonitor> monitor = Optional.empty();
		ISelection currentSelection = HandlerUtil.getCurrentSelectionChecked(event);
		List<?> list = SelectionUtil.toList(currentSelection);

		Set<IJavaProject> javaProjectSet = new HashSet<>();

		if (list != null) {
			try {
				for (Object obj : list) {
					if (obj instanceof IJavaElement) {
						IJavaElement jElem = (IJavaElement) obj;
						switch (jElem.getElementType()) {
						case IJavaElement.METHOD:
							break;
						case IJavaElement.TYPE:
							break;
						case IJavaElement.COMPILATION_UNIT:
							break;
						case IJavaElement.PACKAGE_FRAGMENT:
							break;
						case IJavaElement.PACKAGE_FRAGMENT_ROOT:
							break;
						case IJavaElement.JAVA_PROJECT:
							javaProjectSet.add((IJavaProject) jElem);
							break;
						}
					}
				}

				Shell shell = HandlerUtil.getActiveShellChecked(event);
				ConvertStreamToParallelRefactoringWizard.startRefactoring(
						javaProjectSet.toArray(new IJavaProject[javaProjectSet.size()]), shell, Optional.empty());
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
				throw new ExecutionException("Failed to start refactoring", e);
			}
		}
		// TODO: What do we do if there was no input? Do we display some
		// message?
		return null;
	}

	private Set<IMethod> extractMethodsFromClass(IType type, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();

		if (type.isClass()) {
			for (IMethod method : type.getMethods())
				// if (RefactoringAvailabilityTester.isInterfaceMigrationAvailable(method,
				// monitor)) {
				if (true) {
					this.logPossiblyMigratableMethod(method);
					methodSet.add(method);
				} else
					this.logNonMigratableMethod(method);

		}

		return methodSet;
	}

	private Set<IMethod> extractMethodsFromCompilationUnit(ICompilationUnit cu, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();
		IType[] types = cu.getTypes();

		for (IType iType : types)
			methodSet.addAll(this.extractMethodsFromClass(iType, monitor));

		return methodSet;
	}

	private Set<IMethod> extractMethodsFromJavaProject(IJavaProject jProj, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();

		IPackageFragmentRoot[] roots = jProj.getPackageFragmentRoots();
		for (IPackageFragmentRoot iPackageFragmentRoot : roots)
			methodSet.addAll(this.extractMethodsFromPackageFragmentRoot(iPackageFragmentRoot, monitor));

		return methodSet;
	}

	private Set<IMethod> extractMethodsFromPackageFragment(IPackageFragment frag, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();
		ICompilationUnit[] units = frag.getCompilationUnits();

		for (ICompilationUnit iCompilationUnit : units)
			methodSet.addAll(this.extractMethodsFromCompilationUnit(iCompilationUnit, monitor));

		return methodSet;
	}

	private Set<IMethod> extractMethodsFromPackageFragmentRoot(IPackageFragmentRoot root,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		Set<IMethod> methodSet = new HashSet<>();

		IJavaElement[] children = root.getChildren();
		for (IJavaElement child : children)
			if (child.getElementType() == IJavaElement.PACKAGE_FRAGMENT)
				methodSet.addAll(this.extractMethodsFromPackageFragment((IPackageFragment) child, monitor));

		return methodSet;
	}

	private void logMethod(IMethod method, String format) {
		Formatter formatter = new Formatter();

		formatter.format(format, JavaElementLabels.getElementLabel(method, JavaElementLabels.ALL_FULLY_QUALIFIED));

		JavaPlugin.log(new Status(IStatus.INFO, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
				formatter.toString()));

		formatter.close();
	}

	private void logNonMigratableMethod(IMethod method) {
		this.logMethod(method, "Method: %s is not migratable.");
	}

	private void logPossiblyMigratableMethod(IMethod method) {
		this.logMethod(method, "Method: %s is possibly migratable.");
	}
}