package edu.cuny.hunter.streamrefactoring.eval.handlers;

import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.osgi.framework.FrameworkUtil;

import edu.cuny.hunter.streamrefactoring.eval.utils.Util;

public class StreamMethodCallFinder extends AbstractHandler {

	private static int getNumberOfCompilationUnits(IJavaProject[] javaProjects) throws JavaModelException {
		int ret = 0;
		for (IJavaProject javaProject : javaProjects) {
			IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();

			for (IPackageFragmentRoot root : roots) {
				IJavaElement[] children = root.getChildren();

				for (IJavaElement child : children)
					if (child.getElementType() == IJavaElement.PACKAGE_FRAGMENT) {
						IPackageFragment fragment = (IPackageFragment) child;
						ICompilationUnit[] units = fragment.getCompilationUnits();
						ret += units.length;
					}
			}
		}
		return ret;
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Job.create("Finding stream method calls...", monitor -> {
			try (CSVPrinter printer = new CSVPrinter(new FileWriter("stream_calls.csv"),
					CSVFormat.DEFAULT.withHeader("subject", "method", "calls"))) {

				IJavaProject[] javaProjects = Util.getSelectedJavaProjectsFromEvent(event);

				int work = getNumberOfCompilationUnits(javaProjects);

				SubMonitor subMonitor = SubMonitor.convert(monitor, "Analyzing projects.", work);

				for (IJavaProject javaProject : javaProjects) {
					if (!javaProject.isStructureKnown())
						throw new IllegalStateException(
								String.format("Project: %s should compile beforehand.", javaProject.getElementName()));

					Map<String, Integer> calledMethodNameToCount = new HashMap<>();

					IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();

					for (IPackageFragmentRoot root : roots) {
						IJavaElement[] children = root.getChildren();

						for (IJavaElement child : children)
							if (child.getElementType() == IJavaElement.PACKAGE_FRAGMENT) {
								IPackageFragment fragment = (IPackageFragment) child;
								ICompilationUnit[] units = fragment.getCompilationUnits();

								for (ICompilationUnit unit : units) {
									ASTParser parser = ASTParser.newParser(AST.JLS12);
									parser.setSource(unit);
									parser.setResolveBindings(true);
									ASTNode node = parser.createAST(subMonitor.split(1));

									node.accept(new ASTVisitor(false) {

										@Override
										public boolean visit(MethodInvocation node) {
											ITypeBinding declaringClass = node.resolveMethodBinding()
													.getDeclaringClass().getErasure();

											if (declaringClass.getPackage().getName().startsWith("java.util.stream")) {
												String declaringClassName = declaringClass.getQualifiedName();
												SimpleName methodName = node.getName();
												String qualfiedMethodName = declaringClassName + "." + methodName;

												calledMethodNameToCount.merge(qualfiedMethodName, 1, Integer::sum);
											}
											return super.visit(node);
										}
									});
								}
							}
					}

					for (Entry<String, Integer> entry : calledMethodNameToCount.entrySet())
						printer.printRecord(javaProject.getElementName(), entry.getKey(), entry.getValue());
				}

			} catch (Exception e) {
				return new Status(IStatus.ERROR, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
						"Encountered exception during evaluation", e);
			} finally {
				SubMonitor.done(monitor);
			}

			return new Status(IStatus.OK, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
					"Finding successful.");
		}).schedule();

		return null;
	}
}
