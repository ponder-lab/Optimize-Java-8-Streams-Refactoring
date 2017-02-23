package edu.cuny.hunter.streamrefactoring.eval.handlers;

import static edu.cuny.hunter.streamrefactoring.core.utils.Util.createConvertToParallelStreamRefactoringProcessor;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

import edu.cuny.hunter.streamrefactoring.core.analysis.Stream;
import edu.cuny.hunter.streamrefactoring.core.refactorings.ConvertToParallelStreamRefactoringProcessor;
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
public class EvaluateConvertToParallelStreamRefactoringHandler extends AbstractHandler {

	private static final int LOGGING_LEVEL = IStatus.INFO;
	private static final boolean BUILD_WORKSPACE = false;
	private static final String PERFORM_CHANGE_PROPERTY_KEY = "edu.cuny.hunter.streamrefactoring.eval.performChange";
	private static final boolean PERFORM_CHANGE_DEFAULT = false;

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
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

			try {
				if (BUILD_WORKSPACE) {
					// build the workspace.
					monitor.beginTask("Building workspace ...", IProgressMonitor.UNKNOWN);
					ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD,
							new SubProgressMonitor(monitor, IProgressMonitor.UNKNOWN));
				}

				IJavaProject[] javaProjects = Util.getSelectedJavaProjectsFromEvent(event);

				resultsPrinter = createCSVPrinter("results.csv",
						new String[] { "subject", "#streams", "#optimization available streams", "#optimizable streams",
								"#failed preconditions", "time (s)" });

				candidateStreamPrinter = createCSVPrinter("candidate_streams.csv",
						new String[] { "stream", "start pos", "length", "method", "type FQN" });

				optimizedStreamPrinter = createCSVPrinter("optimizable_streams.csv",
						new String[] { "subject", "stream", "start pos", "length", "method", "type FQN" });

				nonOptimizedStreamPrinter = createCSVPrinter("unoptimizable_streams.csv",
						new String[] { "subject", "stream", "start pos", "length", "method", "type FQN" });

				errorPrinter = createCSVPrinter("failed_preconditions.csv",
						new String[] { "stream", "start pos", "length", "method", "type FQN", "code", "message" });

				streamAttributesPrinter = createCSVPrinter("stream_attributes.csv", new String[] { "stream",
						"start pos", "length", "method", "type FQN", "execution mode", "ordering", "status" });

				for (IJavaProject javaProject : javaProjects) {
					if (!javaProject.isStructureKnown())
						throw new IllegalStateException(
								String.format("Project: %s should compile beforehand.", javaProject.getElementName()));

					// subject.
					resultsPrinter.print(javaProject.getElementName());

					TimeCollector resultsTimeCollector = new TimeCollector();

					resultsTimeCollector.start();
					ConvertToParallelStreamRefactoringProcessor processor = createConvertToParallelStreamRefactoringProcessor(
							new IJavaProject[] { javaProject }, Optional.of(monitor));
					resultsTimeCollector.stop();
					ConvertToParallelStreamRefactoringProcessor.setLoggingLevel(LOGGING_LEVEL);

					// run the precondition checking.
					resultsTimeCollector.start();
					RefactoringStatus status = new ProcessorBasedRefactoring(processor)
							.checkAllConditions(new NullProgressMonitor());
					resultsTimeCollector.stop();

					// #streams.
					resultsPrinter.print(processor.getStreamSet().size());

					// #optimization available streams. TODO: There's no
					// filtering at the moment.
					resultsPrinter.print(processor.getStreamSet().size());

					// candidate streams and their attributes.
					for (Stream stream : processor.getStreamSet()) {
						candidateStreamPrinter.printRecord(stream.getCreation(),
								stream.getCreation().getStartPosition(), stream.getCreation().getLength(),
								Util.getMethodIdentifier(stream.getEnclosingEclipseMethod()),
								stream.getEnclosingType().getFullyQualifiedName());

						streamAttributesPrinter.printRecord(stream.getCreation(),
								stream.getCreation().getStartPosition(), stream.getCreation().getLength(),
								Util.getMethodIdentifier(stream.getEnclosingEclipseMethod()),
								stream.getEnclosingType().getFullyQualifiedName(), stream.getExecutionMode(),
								stream.getOrdering(), stream.getStatus().isOK() ? 0
										: stream.getStatus().getEntryWithHighestSeverity().getSeverity());
					}

					// #optimizable streams.
					Set<Stream> optimizableStreams = processor.getOptimizableStreams();
					resultsPrinter.print(optimizableStreams.size()); // number.

					for (Stream stream : optimizableStreams) {
						optimizedStreamPrinter.printRecord(javaProject.getElementName(), stream.getCreation(),
								stream.getCreation().getStartPosition(), stream.getCreation().getLength(),
								Util.getMethodIdentifier(stream.getEnclosingEclipseMethod()),
								stream.getEnclosingType().getFullyQualifiedName());
					}

					// failed streams.
					for (Stream stream : processor.getUnoptimizableStreams()) {
						nonOptimizedStreamPrinter.printRecord(javaProject.getElementName(), stream.getCreation(),
								stream.getCreation().getStartPosition(), stream.getCreation().getLength(),
								Util.getMethodIdentifier(stream.getEnclosingEclipseMethod()),
								stream.getEnclosingType().getFullyQualifiedName());
					}

					// failed preconditions.
					List<RefactoringStatusEntry> errorEntries = Arrays.stream(status.getEntries())
							.filter(RefactoringStatusEntry::isError).collect(Collectors.toList());
					resultsPrinter.print(errorEntries.size()); // number.

					for (RefactoringStatusEntry entry : errorEntries) {
						if (!entry.isFatalError()) {
							Object correspondingElement = entry.getData();

							if (!(correspondingElement instanceof Stream))
								throw new IllegalStateException("The element: " + correspondingElement
										+ " corresponding to a failed precondition is not a Stream. Instead, it is a: "
										+ correspondingElement.getClass());

							Stream failedStream = (Stream) correspondingElement;
							errorPrinter.printRecord(failedStream.getCreation(),
									failedStream.getCreation().getStartPosition(),
									failedStream.getCreation().getLength(),
									Util.getMethodIdentifier(failedStream.getEnclosingEclipseMethod()),
									failedStream.getEnclosingType().getFullyQualifiedName(), entry.getCode(),
									entry.getMessage());
						}
					}

					// actually perform the refactoring if there are no fatal
					// errors.
					if (shouldPerformChange()) {
						if (!status.hasFatalError()) {
							resultsTimeCollector.start();
							Change change = new ProcessorBasedRefactoring(processor)
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

	private boolean shouldPerformChange() {
		String performChangePropertyValue = System.getenv(PERFORM_CHANGE_PROPERTY_KEY);

		if (performChangePropertyValue == null)
			return PERFORM_CHANGE_DEFAULT;
		else
			return Boolean.valueOf(performChangePropertyValue);
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