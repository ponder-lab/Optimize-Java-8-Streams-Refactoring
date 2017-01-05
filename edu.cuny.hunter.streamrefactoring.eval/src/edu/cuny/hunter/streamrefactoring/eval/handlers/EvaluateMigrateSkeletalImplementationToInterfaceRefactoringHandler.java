package edu.cuny.hunter.streamrefactoring.eval.handlers;

import static com.google.common.io.Files.touch;
import static edu.cuny.hunter.streamrefactoring.core.utils.Util.createMigrateSkeletalImplementationToInterfaceRefactoringProcessor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;
import org.osgi.framework.FrameworkUtil;

import edu.cuny.hunter.streamrefactoring.core.refactorings.MigrateSkeletalImplementationToInterfaceRefactoringProcessor;
import edu.cuny.hunter.streamrefactoring.core.utils.RefactoringAvailabilityTester;
import edu.cuny.hunter.streamrefactoring.core.utils.SkeletalImplementatonClassRemovalUtils;
import edu.cuny.hunter.streamrefactoring.core.utils.TimeCollector;
import edu.cuny.hunter.streamrefactoring.eval.utils.Util;
import net.sourceforge.metrics.core.Metric;
import net.sourceforge.metrics.core.sources.AbstractMetricSource;
import net.sourceforge.metrics.core.sources.Dispatcher;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class EvaluateMigrateSkeletalImplementationToInterfaceRefactoringHandler extends AbstractHandler {

	private static final String ALLOW_CONCRETE_CLASSES_PROPERTY_KEY = "edu.cuny.hunter.streamrefactoring.eval.allowConcreteClasses";
	private static final boolean ALLOW_CONCRETE_CLASSES_DEFAULT = false;
	private static final boolean BUILD_WORKSPACE = false;
	private static final String PERFORM_CHANGE_PROPERTY_KEY = "edu.cuny.hunter.streamrefactoring.eval.performChange";
	private static final boolean PERFORM_CHANGE_DEFAULT = false;

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Job.create("Evaluating Migrate Skeletal Implementation to Interface Refactoring ...", monitor -> {
			CSVPrinter resultsPrinter = null;
			CSVPrinter candidateMethodPrinter = null;
			CSVPrinter migratableMethodPrinter = null;
			CSVPrinter unmigratableMethodPrinter = null;
			CSVPrinter errorPrinter = null;
			CSVPrinter skeletalRemovalErrorPrinter = null;

			try {
				if (BUILD_WORKSPACE) {
					// build the workspace.
					monitor.beginTask("Building workspace ...", IProgressMonitor.UNKNOWN);
					ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD,
							new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
				}

				IJavaProject[] javaProjects = Util.getSelectedJavaProjectsFromEvent(event);

				resultsPrinter = createCSVPrinter("results.csv", new String[] { "subject", "#methods",
						"#migration available methods", "declaring type class hierarchy size",
						"declaring type super interfaces", "#migratable methods", "migratable methods LOC",
						"#unique destination interface subtypes", "#skeletal implementation classes",
						"#removable skeletal implementation classes",
						"removable skeletal implementation class references", "#failed skeletal removal conditions",
						"#failed preconditions", "#methods after refactoring", "time (s)" });
				candidateMethodPrinter = createCSVPrinter("candidate_methods.csv", new String[] { "method", "type FQN",
						"declaring type class hierarchy size", "declaring type super interfaces" });
				migratableMethodPrinter = createCSVPrinter("migratable_methods.csv", new String[] { "subject", "method",
						"type FQN", "destination interface FQN", "MLOC", "#destination interface subtypes" });

				unmigratableMethodPrinter = createCSVPrinter("unmigratable_methods.csv",
						new String[] { "subject", "method", "type FQN", "destination interface FQN" });
				errorPrinter = createCSVPrinter("failed_preconditions.csv",
						new String[] { "method", "type FQN", "destination interface FQN", "code", "message" });
				skeletalRemovalErrorPrinter = createCSVPrinter("failed_skeletal_removals.csv",
						new String[] { "type FQN", "message" });

				for (IJavaProject javaProject : javaProjects) {
					if (!javaProject.isStructureKnown())
						throw new IllegalStateException(
								String.format("Project: %s should compile beforehand.", javaProject.getElementName()));

					resultsPrinter.print(javaProject.getElementName());

					/*
					 * TODO: We probably need to filter these. Actually, we
					 * could use the initial precondition check for filtering (I
					 * think) but there's enough TODOs in there that it's not
					 * possible right now.
					 */
					Set<IMethod> allMethods = getAllMethods(javaProject);
					resultsPrinter.print(allMethods.size());

					Set<IMethod> interfaceMigrationAvailableMethods = new HashSet<IMethod>();

					TimeCollector resultsTimeCollector = new TimeCollector();

					// TODO: Remove this and just clear caches after this call?
					resultsTimeCollector.start();
					for (IMethod method : allMethods) {
						boolean allowConcreteClasses = shouldAllowConcreteClasses();
						if (RefactoringAvailabilityTester.isInterfaceMigrationAvailable(method, allowConcreteClasses,
								Optional.of(monitor)))
							interfaceMigrationAvailableMethods.add(method);
					}
					resultsTimeCollector.stop();

					resultsPrinter.print(interfaceMigrationAvailableMethods.size());

					// candidate methods.
					int declaringTypeClassHierarchyLengthTotal = 0;
					int declaringTypeSuperInterfacesTotal = 0;

					for (IMethod method : interfaceMigrationAvailableMethods) {
						ITypeHierarchy declaringTypeTypeHierarchy = method.getDeclaringType()
								.newTypeHierarchy(new NullProgressMonitor());
						int declaringTypeClassHierarchyLength = declaringTypeTypeHierarchy.getAllClasses().length;
						declaringTypeClassHierarchyLengthTotal += declaringTypeClassHierarchyLength;

						int declaringTypeSuperInterfaces = declaringTypeTypeHierarchy
								.getAllSuperInterfaces(method.getDeclaringType()).length;
						declaringTypeSuperInterfacesTotal += declaringTypeSuperInterfaces;

						candidateMethodPrinter.printRecord(Util.getMethodIdentifier(method),
								method.getDeclaringType().getFullyQualifiedName(), declaringTypeClassHierarchyLength,
								declaringTypeSuperInterfaces);
					}

					resultsPrinter.print(declaringTypeClassHierarchyLengthTotal);
					resultsPrinter.print(declaringTypeSuperInterfacesTotal);

					resultsTimeCollector.start();
					MigrateSkeletalImplementationToInterfaceRefactoringProcessor processor = createMigrateSkeletalImplementationToInterfaceRefactoringProcessor(
							javaProject, interfaceMigrationAvailableMethods.toArray(
									new IMethod[interfaceMigrationAvailableMethods.size()]),
							Optional.of(monitor));
					resultsTimeCollector.stop();
					MigrateSkeletalImplementationToInterfaceRefactoringProcessor.setLoggingLevel(IStatus.WARNING);

					System.out.println(
							"Original methods before preconditions: " + interfaceMigrationAvailableMethods.size());
					System.out.println("Source methods before preconditions: " + processor.getSourceMethods().size());
					System.out.println(
							"Unmigratable methods before preconditions: " + processor.getUnmigratableMethods().size());
					System.out.println(
							"Migratable methods before preconditions: " + processor.getMigratableMethods().size());

					// run the precondition checking.
					resultsTimeCollector.start();
					ProcessorBasedRefactoring processorBasedRefactoring = new ProcessorBasedRefactoring(processor);
					RefactoringStatus status = processorBasedRefactoring.checkAllConditions(new NullProgressMonitor());
					resultsTimeCollector.stop();

					System.out.println(
							"Original methods after preconditions: " + interfaceMigrationAvailableMethods.size());
					System.out.println("Source methods after preconditions: " + processor.getSourceMethods().size());

					File file = new File("source.txt");
					touch(file);
					Files.write(
							file.toPath(), processor.getSourceMethods().parallelStream()
									.map(m -> m.getHandleIdentifier()).collect(Collectors.toSet()),
							StandardOpenOption.APPEND);

					System.out.println(
							"Unmigratable methods after preconditions: " + processor.getUnmigratableMethods().size());

					file = new File("unmigratable.txt");
					touch(file);
					Files.write(
							file.toPath(), processor.getUnmigratableMethods().parallelStream()
									.map(m -> m.getHandleIdentifier()).collect(Collectors.toSet()),
							StandardOpenOption.APPEND);

					System.out.println(
							"Migratable methods after preconditions: " + processor.getMigratableMethods().size());

					file = new File("migratable.txt");
					touch(file);
					Files.write(
							file.toPath(), processor.getMigratableMethods().parallelStream()
									.map(m -> m.getHandleIdentifier()).collect(Collectors.toSet()),
							StandardOpenOption.APPEND);

					// passed methods.
					resultsPrinter.print(processor.getMigratableMethods().size()); // number.

					int totalMethodLinesOfCode = 0;
					Set<IType> destinationInterfaceSubtypes = new HashSet<>();
					Set<IType> declaringTypesWithMigratableMethods = new HashSet<>();
					Set<IType> removableDeclaringTypesWithMigratableMethods = new HashSet<>();
					Map<IType, Set<String>> unremovableDeclaringTypeToReasons = new HashMap<>();

					for (IMethod method : processor.getMigratableMethods()) {
						int methodLinesOfCode = getMethodLinesOfCode(method);
						totalMethodLinesOfCode += methodLinesOfCode;

						IMethod targetMethod = MigrateSkeletalImplementationToInterfaceRefactoringProcessor
								.getTargetMethod(method, Optional.empty());

						IType[] allSubtypes = getAllDeclaringTypeSubtypes(targetMethod);
						destinationInterfaceSubtypes.addAll(Arrays.asList(allSubtypes));

						declaringTypesWithMigratableMethods.add(method.getDeclaringType());

						resultsTimeCollector.start();
						RefactoringStatus removalStatus = SkeletalImplementatonClassRemovalUtils.checkRemoval(method,
								targetMethod.getDeclaringType(), method.getDeclaringType(),
								processor.getMigratableMethods(), Optional.empty());
						resultsTimeCollector.stop();

						if (removalStatus.isOK())
							removableDeclaringTypesWithMigratableMethods.add(method.getDeclaringType());
						else {
							for (RefactoringStatusEntry entry : removalStatus.getEntries()) {
								Set<String> set = unremovableDeclaringTypeToReasons.get(method.getDeclaringType());
								if (set == null) {
									set = new HashSet<>();
									unremovableDeclaringTypeToReasons.put(method.getDeclaringType(), set);
								}
								set.add(entry.getMessage());

								skeletalRemovalErrorPrinter.printRecord(
										method.getDeclaringType().getFullyQualifiedName(), entry.getMessage());
							}
						}

						migratableMethodPrinter.printRecord(javaProject.getElementName(),
								Util.getMethodIdentifier(method), method.getDeclaringType().getFullyQualifiedName(),
								getDestinationTypeFullyQualifiedName(method, monitor), methodLinesOfCode,
								allSubtypes.length);
					}

					// MLOC.
					resultsPrinter.print(totalMethodLinesOfCode);
					// #unique destination interface subtypes.
					resultsPrinter.print(destinationInterfaceSubtypes.size());
					// declaring types with migratable methods.
					resultsPrinter.print(declaringTypesWithMigratableMethods.size());
					// removable declaring types with migratable methods.
					resultsPrinter.print(removableDeclaringTypesWithMigratableMethods.size());
					// removable declaring types with migratable methods
					// references.
					resultsPrinter.print(findReferences(removableDeclaringTypesWithMigratableMethods).size());
					// unremovable declaring types with migratable methods
					// failures.
					resultsPrinter.print(unremovableDeclaringTypeToReasons.values().stream().flatMap(s -> s.stream())
							.collect(Collectors.toList()).size());

					// failed methods.
					for (IMethod method : processor.getUnmigratableMethods()) {
						unmigratableMethodPrinter.printRecord(javaProject.getElementName(),
								Util.getMethodIdentifier(method), method.getDeclaringType().getFullyQualifiedName(),
								getDestinationTypeFullyQualifiedName(method, monitor));
					}

					// failed preconditions.
					resultsPrinter.print(status.getEntries().length); // number.

					for (RefactoringStatusEntry entry : status.getEntries()) {
						if (!entry.isFatalError()) {
							Object correspondingElement = entry.getData();

							if (!(correspondingElement instanceof IMethod))
								throw new IllegalStateException("The element: " + correspondingElement
										+ " corresponding to a failed precondition is not a method. Instead, it is a: "
										+ correspondingElement.getClass());

							IMethod failedMethod = (IMethod) correspondingElement;
							errorPrinter.printRecord(Util.getMethodIdentifier(failedMethod),
									failedMethod.getDeclaringType().getFullyQualifiedName(),
									getDestinationTypeFullyQualifiedName(failedMethod, monitor), entry.getCode(),
									entry.getMessage());
						}
					}

					// actually perform the refactoring if there are no fatal
					// errors.
					if (shouldPerformChange()) {
						if (!status.hasFatalError()) {
							resultsTimeCollector.start();
							Change change = processorBasedRefactoring
									.createChange(new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
							change.perform(new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
							resultsTimeCollector.stop();
						}
					}

					// ensure that we can build the project.
					if (!javaProject.isConsistent())
						javaProject.makeConsistent(new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));

					if (!javaProject.isStructureKnown())
						throw new IllegalStateException(String.format("Project: %s should compile after refactoring.",
								javaProject.getElementName()));

					// TODO: Count warnings?

					// count the new number of methods.
					resultsPrinter.print(getAllMethods(javaProject).size());

					// overall results time.
					resultsPrinter.print((resultsTimeCollector.getCollectedTime()
							- processor.getExcludedTimeCollector().getCollectedTime()) / 1000.0);

					// end the record.
					resultsPrinter.println();
				}
			} catch (Exception e) {
				return new Status(IStatus.ERROR, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
						"Encountered exception during evaluation", e);
			} finally {
				try {
					// closing the files writer after done writing
					if (resultsPrinter != null)
						resultsPrinter.close();
					if (candidateMethodPrinter != null)
						candidateMethodPrinter.close();
					if (migratableMethodPrinter != null)
						migratableMethodPrinter.close();
					if (unmigratableMethodPrinter != null)
						unmigratableMethodPrinter.close();
					if (errorPrinter != null)
						errorPrinter.close();
					if (skeletalRemovalErrorPrinter != null)
						skeletalRemovalErrorPrinter.close();
				} catch (IOException e) {
					return new Status(IStatus.ERROR, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
							"Encountered exception during file closing", e);
				}
			}

			return new Status(IStatus.OK, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
					"Evaluation successful.");
		}).schedule();

		return null;
	}

	private Set<SearchMatch> findReferences(Set<? extends IJavaElement> elements) throws CoreException {
		Set<SearchMatch> ret = new HashSet<>();
		for (IJavaElement elem : elements) {
			new SearchEngine().search(
					SearchPattern.createPattern(elem, IJavaSearchConstants.REFERENCES, SearchPattern.R_EXACT_MATCH),
					new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					SearchEngine.createWorkspaceScope(), new SearchRequestor() {

						@Override
						public void acceptSearchMatch(SearchMatch match) throws CoreException {
							ret.add(match);
						}
					}, new NullProgressMonitor());
		}
		return ret;
	}

	private static IType[] getAllDeclaringTypeSubtypes(IMethod method) throws JavaModelException {
		IType declaringType = method.getDeclaringType();
		ITypeHierarchy typeHierarchy = declaringType.newTypeHierarchy(new NullProgressMonitor());
		IType[] allSubtypes = typeHierarchy.getAllSubtypes(declaringType);
		return allSubtypes;
	}

	private static int getMethodLinesOfCode(IMethod method) {
		AbstractMetricSource metricSource = Dispatcher.getAbstractMetricSource(method);

		if (metricSource != null) {
			Metric value = metricSource.getValue("MLOC");
			int mLOC = value.intValue();
			return mLOC;
		} else {
			System.err.println("WARNING: Could not retrieve metric source for method: " + method);
			return 0;
		}
	}

	private boolean shouldAllowConcreteClasses() {
		String allowConcreateClassesPropertyValue = System.getenv(ALLOW_CONCRETE_CLASSES_PROPERTY_KEY);

		if (allowConcreateClassesPropertyValue == null)
			return ALLOW_CONCRETE_CLASSES_DEFAULT;
		else
			return Boolean.valueOf(allowConcreateClassesPropertyValue);
	}

	private boolean shouldPerformChange() {
		String performChangePropertyValue = System.getenv(PERFORM_CHANGE_PROPERTY_KEY);

		if (performChangePropertyValue == null)
			return PERFORM_CHANGE_DEFAULT;
		else
			return Boolean.valueOf(performChangePropertyValue);
	}

	private static String getDestinationTypeFullyQualifiedName(IMethod method, IProgressMonitor monitor)
			throws JavaModelException {
		return MigrateSkeletalImplementationToInterfaceRefactoringProcessor
				.getTargetMethod(method, Optional.of(monitor)).getDeclaringType().getFullyQualifiedName();
	}

	private static Set<IMethod> getAllMethods(IJavaProject javaProject) throws JavaModelException {
		Set<IMethod> methods = new HashSet<>();

		// collect all methods from this project.
		IPackageFragment[] packageFragments = javaProject.getPackageFragments();
		for (IPackageFragment iPackageFragment : packageFragments) {
			ICompilationUnit[] compilationUnits = iPackageFragment.getCompilationUnits();
			for (ICompilationUnit iCompilationUnit : compilationUnits) {
				IType[] allTypes = iCompilationUnit.getAllTypes();
				for (IType type : allTypes) {
					Collections.addAll(methods, type.getMethods());
				}
			}
		}
		return methods;
	}

	private static CSVPrinter createCSVPrinter(String fileName, String[] header) throws IOException {
		return new CSVPrinter(new FileWriter(fileName, true), CSVFormat.EXCEL.withHeader(header));
	}
}