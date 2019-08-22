package edu.cuny.hunter.streamrefactoring.eval.handlers;

import static edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames.LOGGER_NAME;
import static edu.cuny.hunter.streamrefactoring.core.utils.Util.createConvertToParallelStreamRefactoringProcessor;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
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

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.ibm.wala.ipa.callgraph.Entrypoint;

import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionFailure;
import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionSuccess;
import edu.cuny.hunter.streamrefactoring.core.analysis.Refactoring;
import edu.cuny.hunter.streamrefactoring.core.analysis.Stream;
import edu.cuny.hunter.streamrefactoring.core.analysis.TransformationAction;
import edu.cuny.hunter.streamrefactoring.core.refactorings.OptimizeStreamsRefactoringProcessor;
import edu.cuny.hunter.streamrefactoring.core.utils.TimeCollector;
import edu.cuny.hunter.streamrefactoring.eval.utils.Util;
import net.sourceforge.metrics.core.Constants;
import net.sourceforge.metrics.core.Metric;
import net.sourceforge.metrics.core.sources.AbstractMetricSource;
import net.sourceforge.metrics.core.sources.Dispatcher;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 *
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class EvaluateConvertToParallelStreamRefactoringHandler extends AbstractHandler {

	private static final boolean BUILD_WORKSPACE = false;

	private static final String EVALUATION_PROPERTIES_FILE_NAME = "eval.properties";

	private static final boolean FIND_IMPLICIT_BENCHMARK_ENTRYPOINTS_DEFAULT = false;

	private static final String FIND_IMPLICIT_BENCHMARK_ENTRYPOINTS_PROPERTY_KEY = "edu.cuny.hunter.streamrefactoring.eval.findImplicitBenchmarkEntrypoints";

	private static final boolean FIND_IMPLICIT_ENTRYPOINTS_DEFAULT = false;

	private static final String FIND_IMPLICIT_ENTRYPOINTS_PROPERTY_KEY = "edu.cuny.hunter.streamrefactoring.eval.findImplicitEntrypoints";

	private static final boolean FIND_IMPLICIT_JAVAFX_ENTRYPOINTS_DEFAULT = false;

	private static final String FIND_IMPLICIT_JAVAFX_ENTRYPOINTS_PROPERTY_KEY = "edu.cuny.hunter.streamrefactoring.eval.findImplicitJavaFXEntrypoints";

	private static final boolean FIND_IMPLICIT_TEST_ENTRYPOINTS_DEFAULT = false;

	private static final String FIND_IMPLICIT_TEST_ENTRYPOINTS_PROPERTY_KEY = "edu.cuny.hunter.streamrefactoring.eval.findImplicitTestEntrypoints";

	private static final Logger LOGGER = Logger.getLogger(LOGGER_NAME);

	private static final int LOGGING_LEVEL = IStatus.INFO;

	private static final int N_TO_USE_FOR_STREAMS_DEFAULT = 2;

	private static final String N_TO_USE_FOR_STREAMS_PROPERTY_KEY = "nToUseForStreams";

	private static final boolean PERFORM_ANALYSIS_DEFAULT = true;

	private static final String PERFORM_ANALYSIS_PROPERTY_KEY = "edu.cuny.hunter.streamrefactoring.eval.performAnalysis";

	private static final boolean PERFORM_CHANGE_DEFAULT = false;

	private static final String PERFORM_CHANGE_PROPERTY_KEY = "edu.cuny.hunter.streamrefactoring.eval.performChange";

	private static String[] buildAttributeColumns(String attribute) {
		return new String[] { "subject", "stream", "start pos", "length", "method", "type FQN", attribute };
	}

	private static CSVPrinter createCSVPrinter(String fileName, String[] header) throws IOException {
		return new CSVPrinter(new FileWriter(fileName, true), CSVFormat.EXCEL.withHeader(header));
	}

	private static File findEvaluationPropertiesFile(File directory) {
		if (directory == null)
			return null;

		if (!directory.isDirectory())
			throw new IllegalArgumentException("Expecting directory: " + directory + ".");

		File evaluationFile = directory.toPath().resolve(EVALUATION_PROPERTIES_FILE_NAME).toFile();

		if (evaluationFile != null && evaluationFile.exists())
			return evaluationFile;
		else
			return findEvaluationPropertiesFile(directory.getParentFile());
	}

	private static File findEvaluationPropertiesFile(IJavaProject project) throws JavaModelException {
		IPath location = project.getCorrespondingResource().getLocation();
		return findEvaluationPropertiesFile(location.toFile());
	}

	private static IType[] getAllDeclaringTypeSubtypes(IMethod method) throws JavaModelException {
		IType declaringType = method.getDeclaringType();
		ITypeHierarchy typeHierarchy = declaringType.newTypeHierarchy(new NullProgressMonitor());
		IType[] allSubtypes = typeHierarchy.getAllSubtypes(declaringType);
		return allSubtypes;
	}

	private static Set<IMethod> getAllMethods(IJavaProject javaProject) throws JavaModelException {
		Set<IMethod> methods = new HashSet<>();

		// collect all methods from this project.
		IPackageFragment[] packageFragments = javaProject.getPackageFragments();
		for (IPackageFragment iPackageFragment : packageFragments) {
			ICompilationUnit[] compilationUnits = iPackageFragment.getCompilationUnits();
			for (ICompilationUnit iCompilationUnit : compilationUnits) {
				IType[] allTypes = iCompilationUnit.getAllTypes();
				for (IType type : allTypes)
					Collections.addAll(methods, type.getMethods());
			}
		}
		return methods;
	}

	@SuppressWarnings("unused")
	private static int getMethodLinesOfCode(IMethod method) {
		return getMetric(method, Constants.MLOC);
	}

	private static int getMetric(IJavaElement elem, String key) {
		AbstractMetricSource metricSource = Dispatcher.getAbstractMetricSource(elem);

		if (metricSource != null) {
			Metric value = metricSource.getValue(key);
			int tLOC = value.intValue();
			return tLOC;
		} else {
			System.err.println("WARNING: Could not retrieve metric source for: " + elem.getElementName());
			return 0;
		}
	}

	private static int getNForStreams(IJavaProject project) throws IOException, JavaModelException {
		Properties properties = new Properties();
		File file = findEvaluationPropertiesFile(project);

		if (file != null && file.exists())
			try (Reader reader = new FileReader(file)) {
				properties.load(reader);

				String nToUseForStreams = properties.getProperty(N_TO_USE_FOR_STREAMS_PROPERTY_KEY);

				if (nToUseForStreams == null) {
					int ret = N_TO_USE_FOR_STREAMS_DEFAULT;
					LOGGER.info("Using default N for streams: " + ret + ".");
					return ret;
				} else {
					int ret = Integer.valueOf(nToUseForStreams);
					LOGGER.info("Using properties file N for streams: " + ret + ".");
					return ret;
				}
			}
		else {
			int ret = N_TO_USE_FOR_STREAMS_DEFAULT;
			LOGGER.info("Using default N for streams: " + ret + ".");
			return ret;
		}
	}

	private static int getNumberOfClasses(IJavaProject javaProject) {
		return getMetric(javaProject, Constants.NUM_TYPES);
	}

	private static int getNumberOfMethods(IJavaProject javaProject) {
		return getMetric(javaProject, Constants.NUM_METHODS);
	}

	private static Collection<Entrypoint> getProjectEntryPoints(IJavaProject javaProject,
			OptimizeStreamsRefactoringProcessor processor) {
		return processor.getEntryPoints(javaProject);
	}

	private static int getProjectLinesOfCode(IJavaProject javaProject) {
		return getMetric(javaProject, Constants.TLOC);
	}

	private static void printStreamAttributesWithMultipleValues(Set<?> set, CSVPrinter printer, Stream stream,
			String method, IJavaProject project) throws IOException {
		if (set != null)
			for (Object object : set)
				printer.printRecord(project.getElementName(), stream.getCreation(),
						stream.getCreation().getStartPosition(), stream.getCreation().getLength(), method,
						stream.getEnclosingType().getFullyQualifiedName(), object.toString());
	}

	private static boolean shouldFindImplicitBenchmarkEntrypoints() {
		String findImplicitBenchmarkEntrypoints = System.getenv(FIND_IMPLICIT_BENCHMARK_ENTRYPOINTS_PROPERTY_KEY);

		if (findImplicitBenchmarkEntrypoints == null)
			return FIND_IMPLICIT_BENCHMARK_ENTRYPOINTS_DEFAULT;
		else
			return Boolean.valueOf(findImplicitBenchmarkEntrypoints);
	}

	private static boolean shouldFindImplicitEntrypoints() {
		String findImplicitEntrypoits = System.getenv(FIND_IMPLICIT_ENTRYPOINTS_PROPERTY_KEY);

		if (findImplicitEntrypoits == null)
			return FIND_IMPLICIT_ENTRYPOINTS_DEFAULT;
		else
			return Boolean.valueOf(findImplicitEntrypoits);
	}

	private static boolean shouldFindImplicitJavaFXEntrypoints() {
		String findImplicitJavaFXEntrypoints = System.getenv(FIND_IMPLICIT_JAVAFX_ENTRYPOINTS_PROPERTY_KEY);

		if (findImplicitJavaFXEntrypoints == null)
			return FIND_IMPLICIT_JAVAFX_ENTRYPOINTS_DEFAULT;
		else
			return Boolean.valueOf(findImplicitJavaFXEntrypoints);
	}

	private static boolean shouldFindImplicitTestEntrypoints() {
		String findImplicitTestEntrypoints = System.getenv(FIND_IMPLICIT_TEST_ENTRYPOINTS_PROPERTY_KEY);

		if (findImplicitTestEntrypoints == null)
			return FIND_IMPLICIT_TEST_ENTRYPOINTS_DEFAULT;
		else
			return Boolean.valueOf(findImplicitTestEntrypoints);
	}

	private static boolean shouldPerformAnalysis() {
		String value = System.getenv(PERFORM_ANALYSIS_PROPERTY_KEY);

		if (value == null)
			return PERFORM_ANALYSIS_DEFAULT;
		else
			return Boolean.valueOf(value);
	}

	private static boolean shouldPerformChange() {
		String performChangePropertyValue = System.getenv(PERFORM_CHANGE_PROPERTY_KEY);

		if (performChangePropertyValue == null)
			return PERFORM_CHANGE_DEFAULT;
		else
			return Boolean.valueOf(performChangePropertyValue);
	}

	/**
	 * the command has been executed, so extract extract the needed information from
	 * the application context.
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Job.create("Evaluating Convert To Parallel Stream Refactoring ...", monitor -> {
			CSVPrinter resultsPrinter = null;
			CSVPrinter candidateStreamPrinter = null;
			CSVPrinter optimizedStreamPrinter = null;
			CSVPrinter nonOptimizedStreamPrinter = null;
			CSVPrinter errorPrinter = null;
			CSVPrinter streamAttributesPrinter = null;
			CSVPrinter streamActionsPrinter = null;
			CSVPrinter streamExecutionModePrinter = null;
			CSVPrinter streamOrderingPrinter = null;
			CSVPrinter entryPointsPrinter = null;
			PrintWriter entryPointsTXTPrinter = null;

			OptimizeStreamsRefactoringProcessor processor = null;

			try {
				if (BUILD_WORKSPACE) {
					// build the workspace.
					monitor.beginTask("Building workspace ...", IProgressMonitor.UNKNOWN);
					ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD,
							new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
				}

				IJavaProject[] javaProjects = Util.getSelectedJavaProjectsFromEvent(event);

				List<String> resultsHeader = new ArrayList<>(Arrays.asList("subject", "SLOC", "classes", "methods",
						"#entrypoints", "N", "#streams", "#optimization available streams", "#optimizable streams",
						"#failed preconditions", "stream instances processed", "stream instances skipped"));

				for (Refactoring refactoring : Refactoring.values())
					resultsHeader.add(refactoring.toString());

				for (PreconditionSuccess preconditionSuccess : PreconditionSuccess.values())
					resultsHeader.add(preconditionSuccess.toString());

				for (TransformationAction action : TransformationAction.values())
					resultsHeader.add(action.toString());

				resultsHeader.add("time (s)");

				resultsPrinter = createCSVPrinter("results.csv",
						resultsHeader.toArray(new String[resultsHeader.size()]));

				candidateStreamPrinter = createCSVPrinter("candidate_streams.csv",
						new String[] { "subject", "stream", "start pos", "length", "method", "type FQN" });

				optimizedStreamPrinter = createCSVPrinter("optimizable_streams.csv",
						new String[] { "subject", "stream", "start pos", "length", "method", "type FQN" });

				nonOptimizedStreamPrinter = createCSVPrinter("unoptimizable_streams.csv",
						new String[] { "subject", "stream", "start pos", "length", "method", "type FQN" });

				errorPrinter = createCSVPrinter("failed_preconditions.csv", new String[] { "subject", "stream",
						"start pos", "length", "method", "type FQN", "code", "message" });

				streamAttributesPrinter = createCSVPrinter("stream_attributes.csv",
						new String[] { "subject", "stream", "start pos", "length", "method", "type FQN",
								"side-effects?", "stateful intermediate operations", "reduce ordering possibly matters",
								"refactoring", "passingPrecondition", "status" });

				streamActionsPrinter = createCSVPrinter("stream_actions.csv", buildAttributeColumns("actions"));

				streamExecutionModePrinter = createCSVPrinter("stream_execution_modes.csv",
						buildAttributeColumns("execution mode"));

				streamOrderingPrinter = createCSVPrinter("stream_orderings.csv", buildAttributeColumns("ordering"));

				entryPointsPrinter = createCSVPrinter("entry_points.csv",
						new String[] { "subject", "method", "type FQN" });

				entryPointsTXTPrinter = new PrintWriter("entry_points.txt");

				// set up analysis parameters for all projects.
				boolean shouldFindImplicitEntrypoints = shouldFindImplicitEntrypoints();
				boolean shouldFindImplicitTestEntrypoints = shouldFindImplicitTestEntrypoints();
				boolean shouldFindImplicitBenchmarkEntrypoints = shouldFindImplicitBenchmarkEntrypoints();
				boolean shouldFindImplicitJavaFXEntrypoints = shouldFindImplicitJavaFXEntrypoints();

				for (IJavaProject javaProject : javaProjects) {
					if (!javaProject.isStructureKnown())
						throw new IllegalStateException(
								String.format("Project: %s should compile beforehand.", javaProject.getElementName()));

					// subject.
					resultsPrinter.print(javaProject.getElementName());

					// lines of code
					resultsPrinter.print(getProjectLinesOfCode(javaProject));

					// number of classes.
					resultsPrinter.print(getNumberOfClasses(javaProject));

					// number of methods.
					resultsPrinter.print(getNumberOfMethods(javaProject));

					// set up analysis for single project.
					TimeCollector resultsTimeCollector = new TimeCollector();
					int nToUseForStreams = getNForStreams(javaProject);

					resultsTimeCollector.start();
					processor = createConvertToParallelStreamRefactoringProcessor(new IJavaProject[] { javaProject },
							nToUseForStreams, shouldFindImplicitEntrypoints, shouldFindImplicitTestEntrypoints,
							shouldFindImplicitBenchmarkEntrypoints, shouldFindImplicitJavaFXEntrypoints,
							Optional.of(monitor));
					resultsTimeCollector.stop();
					OptimizeStreamsRefactoringProcessor.setLoggingLevel(LOGGING_LEVEL);

					// run the precondition checking.
					RefactoringStatus status = null;

					if (shouldPerformAnalysis()) {
						resultsTimeCollector.start();
						status = new ProcessorBasedRefactoring(processor).checkAllConditions(new NullProgressMonitor());
						resultsTimeCollector.stop();
					} else
						status = new RefactoringStatus();

					// print entry points.
					Collection<Entrypoint> entryPoints = getProjectEntryPoints(javaProject, processor);
					resultsPrinter.print(entryPoints.size()); // number.

					for (Entrypoint entryPoint : entryPoints) {
						com.ibm.wala.classLoader.IMethod method = entryPoint.getMethod();
						entryPointsPrinter.printRecord(javaProject.getElementName(), method.getSignature(),
								method.getDeclaringClass().getName());
						entryPointsTXTPrinter.println(method.getSignature());
					}

					// N.
					resultsPrinter.print(nToUseForStreams);

					// #streams.
					Set<Stream> streamSet = processor.getStreamSet();
					resultsPrinter.print(streamSet == null ? 0 : streamSet.size());

					// #optimization available streams. These are the "filtered" streams.
					Set<Stream> candidates = streamSet == null ? Collections.emptySet()
							: streamSet.parallelStream().filter(s -> {
								String pluginId = FrameworkUtil.getBundle(Stream.class).getSymbolicName();

								// error related to reachability.
								RefactoringStatusEntry reachabilityError = s.getStatus().getEntryMatchingCode(pluginId,
										PreconditionFailure.STREAM_CODE_NOT_REACHABLE.getCode());

								// error related to missing entry points.
								RefactoringStatusEntry entryPointError = s.getStatus().getEntryMatchingCode(pluginId,
										PreconditionFailure.NO_ENTRY_POINT.getCode());

								// filter streams without such errors.
								return reachabilityError == null && entryPointError == null;
							}).collect(Collectors.toSet());

					resultsPrinter.print(candidates.size()); // number.

					// candidate streams.
					for (Stream stream : candidates)
						candidateStreamPrinter.printRecord(javaProject.getElementName(), stream.getCreation(),
								stream.getCreation().getStartPosition(), stream.getCreation().getLength(),
								Util.getMethodIdentifier(stream.getEnclosingEclipseMethod()),
								stream.getEnclosingType() == null ? null
										: stream.getEnclosingType().getFullyQualifiedName());

					// stream attributes.
					if (streamSet != null)
						for (Stream stream : streamSet) {
							streamAttributesPrinter.printRecord(javaProject.getElementName(), stream.getCreation(),
									stream.getCreation().getStartPosition(), stream.getCreation().getLength(),
									Util.getMethodIdentifier(stream.getEnclosingEclipseMethod()),
									stream.getEnclosingType() == null ? null
											: stream.getEnclosingType().getFullyQualifiedName(),
									stream.hasPossibleSideEffects(), stream.hasPossibleStatefulIntermediateOperations(),
									stream.reduceOrderingPossiblyMatters(), stream.getRefactoring(),
									stream.getPassingPrecondition(), stream.getStatus().isOK() ? 0
											: stream.getStatus().getEntryWithHighestSeverity().getSeverity());

							String method = Util.getMethodIdentifier(stream.getEnclosingEclipseMethod());

							printStreamAttributesWithMultipleValues(stream.getActions(), streamActionsPrinter, stream,
									method, javaProject);

							printStreamAttributesWithMultipleValues(stream.getPossibleExecutionModes(),
									streamExecutionModePrinter, stream, method, javaProject);

							printStreamAttributesWithMultipleValues(stream.getPossibleOrderings(),
									streamOrderingPrinter, stream, method, javaProject);

						}

					// #optimizable streams.
					Set<Stream> optimizableStreams = processor.getOptimizableStreams();
					resultsPrinter.print(optimizableStreams.size()); // number.

					for (Stream stream : optimizableStreams)
						optimizedStreamPrinter.printRecord(javaProject.getElementName(), stream.getCreation(),
								stream.getCreation().getStartPosition(), stream.getCreation().getLength(),
								Util.getMethodIdentifier(stream.getEnclosingEclipseMethod()),
								stream.getEnclosingType().getFullyQualifiedName());

					// failed streams.
					SetView<Stream> failures = Sets.difference(candidates, processor.getOptimizableStreams());

					for (Stream stream : failures)
						nonOptimizedStreamPrinter.printRecord(javaProject.getElementName(), stream.getCreation(),
								stream.getCreation().getStartPosition(), stream.getCreation().getLength(),
								Util.getMethodIdentifier(stream.getEnclosingEclipseMethod()),
								stream.getEnclosingType() == null ? null
										: stream.getEnclosingType().getFullyQualifiedName());

					// failed preconditions.
					Collection<RefactoringStatusEntry> errorEntries = failures.parallelStream().map(Stream::getStatus)
							.flatMap(s -> Arrays.stream(s.getEntries())).filter(RefactoringStatusEntry::isError)
							.collect(Collectors.toSet());

					resultsPrinter.print(errorEntries.size()); // number.

					for (RefactoringStatusEntry entry : errorEntries)
						if (!entry.isFatalError()) {
							Object correspondingElement = entry.getData();

							if (!(correspondingElement instanceof Stream))
								throw new IllegalStateException("The element: " + correspondingElement
										+ " corresponding to a failed precondition is not a Stream. Instead, it is a: "
										+ correspondingElement.getClass());

							Stream failedStream = (Stream) correspondingElement;

							errorPrinter.printRecord(javaProject.getElementName(), failedStream.getCreation(),
									failedStream.getCreation().getStartPosition(),
									failedStream.getCreation().getLength(),
									Util.getMethodIdentifier(failedStream.getEnclosingEclipseMethod()),
									failedStream.getEnclosingType() == null ? null
											: failedStream.getEnclosingType().getFullyQualifiedName(),
									entry.getCode(), entry.getMessage());
						}

					// #stream instances analyzed.
					resultsPrinter.print(processor.getNumberOfProcessedStreamInstances());

					// #stream instances skipped.
					resultsPrinter.print(processor.getNumberOfSkippedStreamInstances());

					// Refactoring type counts.
					for (Refactoring refactoring : Refactoring.values())
						resultsPrinter.print(streamSet == null ? 0
								: streamSet.parallelStream().map(Stream::getRefactoring)
										.filter(r -> Objects.equals(r, refactoring)).count());

					// Precondition success counts.
					for (PreconditionSuccess preconditionSuccess : PreconditionSuccess.values())
						resultsPrinter.print(streamSet == null ? 0
								: streamSet.parallelStream().map(Stream::getPassingPrecondition)
										.filter(pp -> Objects.equals(pp, preconditionSuccess)).count());

					// Transformation counts.
					for (TransformationAction action : TransformationAction.values())
						resultsPrinter.print(streamSet == null ? 0
								: streamSet.parallelStream().map(Stream::getActions).filter(Objects::nonNull)
										.flatMap(as -> as.parallelStream()).filter(a -> Objects.equals(a, action))
										.count());

					// actually perform the refactoring if there are no fatal
					// errors.
					if (shouldPerformChange())
						if (!status.hasFatalError()) {
							resultsTimeCollector.start();
							Change change = new ProcessorBasedRefactoring(processor)
									.createChange(new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
							change.perform(new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
							resultsTimeCollector.stop();
						}

					// ensure that we can build the project.
					if (!javaProject.isConsistent())
						javaProject.makeConsistent(new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));

					if (!javaProject.isStructureKnown())
						throw new IllegalStateException(String.format("Project: %s should compile after refactoring.",
								javaProject.getElementName()));

					// TODO: Count warnings?

					// overall results time.
					resultsPrinter.print((resultsTimeCollector.getCollectedTime()
							- processor.getExcludedTimeCollector().getCollectedTime()) / 1000.0);

					// end the record.
					resultsPrinter.println();

					// clear the cache.
					processor.clearCaches();
				}
			} catch (Exception e) {
				return new Status(IStatus.ERROR, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
						"Encountered exception during evaluation", e);
			} finally {
				try {
					// closing the files writer after done writing
					if (resultsPrinter != null)
						resultsPrinter.close();
					if (candidateStreamPrinter != null)
						candidateStreamPrinter.close();
					if (optimizedStreamPrinter != null)
						optimizedStreamPrinter.close();
					if (nonOptimizedStreamPrinter != null)
						nonOptimizedStreamPrinter.close();
					if (errorPrinter != null)
						errorPrinter.close();
					if (streamAttributesPrinter != null)
						streamAttributesPrinter.close();
					if (streamActionsPrinter != null)
						streamActionsPrinter.close();
					if (streamExecutionModePrinter != null)
						streamExecutionModePrinter.close();
					if (streamOrderingPrinter != null)
						streamOrderingPrinter.close();
					if (entryPointsPrinter != null)
						entryPointsPrinter.close();
					if (entryPointsTXTPrinter != null)
						entryPointsTXTPrinter.close();

					// clear cache.
					if (processor != null)
						processor.clearCaches();
				} catch (IOException e) {
					SubMonitor.done(monitor);
					return new Status(IStatus.ERROR, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
							"Encountered exception during file closing", e);
				}
				SubMonitor.done(monitor);
			}

			return new Status(IStatus.OK, FrameworkUtil.getBundle(this.getClass()).getSymbolicName(),
					"Evaluation successful.");
		}).schedule();

		return null;
	}

	private Set<SearchMatch> findReferences(Set<? extends IJavaElement> elements) throws CoreException {
		Set<SearchMatch> ret = new HashSet<>();
		for (IJavaElement elem : elements)
			new SearchEngine().search(
					SearchPattern.createPattern(elem, IJavaSearchConstants.REFERENCES, SearchPattern.R_EXACT_MATCH),
					new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
					SearchEngine.createWorkspaceScope(), new SearchRequestor() {

						@Override
						public void acceptSearchMatch(SearchMatch match) throws CoreException {
							ret.add(match);
						}
					}, new NullProgressMonitor());
		return ret;
	}
}
