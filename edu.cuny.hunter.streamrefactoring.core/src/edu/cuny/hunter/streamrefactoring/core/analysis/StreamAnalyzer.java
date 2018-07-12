package edu.cuny.hunter.streamrefactoring.core.analysis;

import static com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.BaseStream;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.rules.TypestateRule;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.scope.JUnitEntryPoints;

import edu.cuny.hunter.streamrefactoring.core.analysis.StreamStateMachine.Statistics;
import edu.cuny.hunter.streamrefactoring.core.messages.Messages;
import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;
import edu.cuny.hunter.streamrefactoring.core.utils.TimeCollector;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

@SuppressWarnings("restriction")
public class StreamAnalyzer extends ASTVisitor {

	private static final String ENTRY_POINT_FILENAME = "entry_points.txt";

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	private static final int N_FOR_STREAMS_DEFAULT = 2;

	private int numberOfMethodForStreamReturnType;

	private int numberOfMethodForStreamParameter;

	private static void addImplicitEntryPoints(Collection<Entrypoint> target, Iterable<Entrypoint> source) {
		for (Entrypoint implicitEntryPoint : source)
			if (target.add(implicitEntryPoint))
				LOGGER.info(() -> "Adding implicit entry point: " + implicitEntryPoint);
	}

	/**
	 * Read entry_points.txt and get a set of method signatures, then, get entry
	 * points by those signatures
	 *
	 * @return a set of entry points
	 * @throws IOException
	 */
	private static Set<Entrypoint> findEntryPointsFromFile(IClassHierarchy classHierarchy, File file)
			throws IOException {
		Set<String> signatures = new HashSet<>();

		try (Scanner scanner = new Scanner(file)) {
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				signatures.add(line);
			}
		}

		Set<Entrypoint> entrypoints = Util.findEntryPoints(classHierarchy, signatures);
		return entrypoints;
	}

	/**
	 * Search entry_points.txt in project directory recursively.
	 *
	 * @param directory
	 *            Project directory.
	 * @param fileName
	 *            The target file.
	 * @return null if the file does not exist and file if we found the file.
	 */
	private static File getEntryPointsFile(IPath directory, String fileName) {
		// If file does not exist, find the file in upper level.
		Path directoryPath = Paths.get(directory.toString());

		File file;
		do {
			file = new File(directoryPath.resolve(ENTRY_POINT_FILENAME).toString());
			directoryPath = directoryPath.getParent();
		} while (!file.exists() && directoryPath != null);

		if (!file.exists())
			return null;
		else
			return file;
	}

	/**
	 * Map from {@link EclipseProjectAnalysisEngine}s that have their
	 * {@link CallGraph}s built to the {@link Entrypoint}s that were used to build
	 * the graph.
	 */
	private Map<EclipseProjectAnalysisEngine<InstanceKey>, Collection<Entrypoint>> enginesWithBuiltCallGraphsToEntrypointsUsed = new HashMap<>();

	private boolean findImplicitBenchmarkEntryPoints;

	private boolean findImplicitEntryPoints = true;

	private boolean findImplicitJavaFXEntryPoints;

	private boolean findImplicitTestEntryPoints;

	/**
	 * The N to use for instances of {@link BaseStream} in the nCFA.
	 */
	private int nForStreams = N_FOR_STREAMS_DEFAULT;

	private int numberOfProcessedStreamInstances;

	private int numberOfSkippedStreamInstances;

	private Set<Stream> streamSet = new HashSet<>();

	public StreamAnalyzer() {
		this(false);
	}

	public StreamAnalyzer(boolean visitDocTags) {
		super(visitDocTags);
	}

	public StreamAnalyzer(boolean visitDocTags, boolean findImplicitEntryPoints) {
		this(visitDocTags);
		this.findImplicitEntryPoints = findImplicitEntryPoints;
	}

	public StreamAnalyzer(boolean visitDocTags, boolean findImplicitEntryPoints, boolean findImplicitTestEntryPoints,
			boolean findImplicitBenchmarkEntryPoints, boolean findImplicitJavaFXEntryPoints) {
		this(visitDocTags, findImplicitEntryPoints);
		this.findImplicitTestEntryPoints = findImplicitTestEntryPoints;
		this.findImplicitBenchmarkEntryPoints = findImplicitBenchmarkEntryPoints;
		this.findImplicitJavaFXEntryPoints = findImplicitJavaFXEntryPoints;
	}

	public StreamAnalyzer(boolean visitDocTags, int nForStreams) {
		super(visitDocTags);
		this.nForStreams = nForStreams;
	}

	public StreamAnalyzer(boolean visitDocTags, int nForStreams, boolean findImplicitEntryPoints) {
		this(visitDocTags, findImplicitEntryPoints);
		this.nForStreams = nForStreams;
	}

	public StreamAnalyzer(boolean visitDocTags, int nForStreams, boolean findImplicitEntryPoints,
			boolean findImplicitTestEntryPoints, boolean findImplicitBenchmarkEntryPoints,
			boolean findImplicitJavaFXEntryPoints) {
		this(visitDocTags, findImplicitEntryPoints, findImplicitTestEntryPoints, findImplicitBenchmarkEntryPoints,
				findImplicitJavaFXEntryPoints);
		this.nForStreams = nForStreams;
	}

	/**
	 * Analyzes this {@link StreamAnalyzer}'s streams.
	 *
	 * @param subMonitor
	 * @param optional
	 *
	 * @return A {@link Map} of project's analyzed along with the entry points used.
	 * @see #analyze(Optional).
	 */
	public Map<IJavaProject, Collection<Entrypoint>> analyze() throws CoreException {
		return this.analyze(Optional.empty(), new NullProgressMonitor());
	}

	/**
	 * Analyzes this {@link StreamAnalyzer}'s streams.
	 *
	 * @param collector
	 *            To exclude from the time certain parts of the analysis.
	 * @return A {@link Map} of project's analyzed along with the entry points used.
	 * @see #analyze().
	 */
	public Map<IJavaProject, Collection<Entrypoint>> analyze(Optional<TimeCollector> collector,
			IProgressMonitor monitor) throws CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, "Analyzing...", IProgressMonitor.UNKNOWN);

		LOGGER.fine(() -> "Using N = " + this.getNForStreams() + ".");

		Map<IJavaProject, Collection<Entrypoint>> ret = new HashMap<>();

		// collect the projects to be analyzed.
		Map<IJavaProject, Set<Stream>> projectToStreams = this.getStreamSet().stream().filter(s -> s.getStatus().isOK())
				.collect(Collectors.groupingBy(Stream::getCreationJavaProject, Collectors.toSet()));

		// process each project.
		subMonitor.beginTask("Processing projects ...", projectToStreams.keySet().size());

		for (IJavaProject project : projectToStreams.keySet()) {
			// create the analysis engine for the project.
			// exclude from the analysis because the IR will be built here.

			collector.ifPresent(TimeCollector::start);
			EclipseProjectAnalysisEngine<InstanceKey> engine = null;
			try {
				engine = new EclipseProjectAnalysisEngine<>(project, this.getNForStreams());
				engine.buildAnalysisScope();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "Could not create analysis engine for: " + project.getElementName(), e);
				throw new RuntimeException(e);
			}
			collector.ifPresent(TimeCollector::stop);

			// build the call graph for the project.
			Collection<Entrypoint> entryPoints = null;
			try {
				entryPoints = this.buildCallGraph(engine, collector,
						subMonitor.split(IProgressMonitor.UNKNOWN, SubMonitor.SUPPRESS_NONE));
			} catch (IOException | CoreException | CancelException e) {
				LOGGER.log(Level.SEVERE,
						"Exception encountered while building call graph for: " + project.getElementName() + ".", e);
				throw new RuntimeException(e);
			}

			// save the entry points.
			ret.put(project, entryPoints);

			Set<Stream> streamSet = projectToStreams.get(project);

			if (entryPoints.isEmpty()) {
				// add a status entry for each stream in the project
				for (Stream stream : streamSet)
					stream.addStatusEntry(PreconditionFailure.NO_ENTRY_POINT,
							"Project: " + engine.getProject().getElementName() + " has no entry points.");
				return ret;
			}

			OrderingInference orderingInference = new OrderingInference(engine.getClassHierarchy());

			subMonitor.beginTask("Inferring initial stream attributes...", streamSet.size());

			for (Iterator<Stream> iterator = streamSet.iterator(); iterator.hasNext();) {
				Stream stream = iterator.next();
				try {
					stream.inferInitialAttributes(engine, orderingInference);
				} catch (InvalidClassFileException | IOException e) {
					LOGGER.log(Level.SEVERE, "Exception encountered while processing: " + stream.getCreation() + ".",
							e);
					throw new RuntimeException(e);
				} catch (UnhandledCaseException e) {
					LOGGER.log(Level.WARNING, "Unhandled case encountered while processing: " + stream.getCreation(),
							e);
					stream.addStatusEntry(PreconditionFailure.CURRENTLY_NOT_HANDLED,
							"Stream: " + stream.getCreation() + " has an unhandled case: " + e.getMessage());
				} catch (StreamCreationNotConsideredException e) {
					LOGGER.log(Level.WARNING, "Unconsidered case encountered while processing: " + stream.getCreation(),
							e);
					// remove it from consideration.
					iterator.remove();
					this.getStreamSet().remove(stream);
				}
				subMonitor.worked(1);
			}

			// start the state machine for each valid stream in the project.
			StreamStateMachine stateMachine = new StreamStateMachine();
			try {
				Map<TypestateRule, StreamStateMachine.Statistics> ruleToStats = stateMachine.start(
						streamSet.parallelStream().filter(s -> s.getStatus().isOK()).collect(Collectors.toSet()),
						engine, orderingInference,
						subMonitor.split(IProgressMonitor.UNKNOWN, SubMonitor.SUPPRESS_NONE));

				// use just one the rules.
				assert !ruleToStats.isEmpty() : "Should have stats available.";
				Statistics statistics = ruleToStats.values().iterator().next();

				this.setNumberOfProcessedStreamInstances(statistics.getNumberOfStreamInstancesProcessed());
				this.setNumberOfSkippedStreamInstances(statistics.getNumberOfStreamInstancesSkipped());
			} catch (PropertiesException | CancelException | NoniterableException | NoninstantiableException
					| CannotExtractSpliteratorException | InvalidClassFileException | IOException e) {
				LOGGER.log(Level.SEVERE, "Error while starting state machine.", e);
				throw new RuntimeException(e);
			}

			// check preconditions.
			SubMonitor checkMonitor = subMonitor.split(IProgressMonitor.UNKNOWN, SubMonitor.SUPPRESS_NONE);
			checkMonitor.beginTask(Messages.CheckingPreconditions, streamSet.size());

			for (Stream stream : streamSet.parallelStream().filter(s -> s.getStatus().isOK())
					.collect(Collectors.toSet())) {
				stream.check();
				checkMonitor.worked(1);
			}

			subMonitor.worked(1);
		} // end for each stream.
		return ret;
	}

	/**
	 * Builds the call graph that is part of the
	 * {@link EclipseProjectAnalysisEngine}.
	 *
	 * @param engine
	 *            The EclipseProjectAnalysisEngine for which to build the call
	 *            graph.
	 * @param collector
	 *            A {@link TimeCollector} to exclude entry point finding.
	 * @return The {@link Entrypoint}s used in building the {@link CallGraph}.
	 */
	protected Collection<Entrypoint> buildCallGraph(EclipseProjectAnalysisEngine<InstanceKey> engine,
			Optional<TimeCollector> collector, IProgressMonitor monitor)
			throws IOException, CoreException, CallGraphBuilderCancelException, CancelException {
		// if we haven't built the call graph yet.
		if (!this.enginesWithBuiltCallGraphsToEntrypointsUsed.keySet().contains(engine)) {
			// find entry points (but exclude it from the time).
			collector.ifPresent(TimeCollector::start);
			Set<Entrypoint> entryPoints;

			// find the entry_points.txt in the project directory
			File entryPointFile = getEntryPointsFile(engine.getProject().getResource().getLocation(),
					ENTRY_POINT_FILENAME);

			// if the file was found,
			if (entryPointFile != null) {
				// find explicit entry points from entry_points.txt. Ignore the explicit
				// (annotation-based) entry points.
				entryPoints = findEntryPointsFromFile(engine.getClassHierarchy(), entryPointFile);
				entryPoints.forEach(ep -> LOGGER.info(() -> "Adding explicit entry point from file: " + ep));
			} else {
				// find explicit entry points.
				entryPoints = Util.findEntryPoints(engine.getClassHierarchy());
				entryPoints.forEach(ep -> LOGGER.info(() -> "Adding explicit entry point: " + ep));

				if (this.shouldFindImplicitEntryPoints()) {
					// also find implicit entry points.
					Iterable<Entrypoint> mainEntrypoints = makeMainEntrypoints(engine.getClassHierarchy().getScope(),
							engine.getClassHierarchy());

					// add them as well.
					addImplicitEntryPoints(entryPoints, mainEntrypoints);
				}

				if (this.shouldFindImplicitTestEntryPoints()) {
					// try to find test entry points.
					Iterable<Entrypoint> jUnitEntryPoints = JUnitEntryPoints.make(engine.getClassHierarchy());

					// add them as well.
					addImplicitEntryPoints(entryPoints, jUnitEntryPoints);
				}

				if (this.shouldFindImplicitBenchmarkEntryPoints()) {
					// try to find benchmark entry points.
					Set<Entrypoint> benchmarkEntryPoints = Util.findBenchmarkEntryPoints(engine.getClassHierarchy());

					// add them as well.
					addImplicitEntryPoints(entryPoints, benchmarkEntryPoints);
				}

				if (this.shouldFindImplicitJavaFXEntryPoints()) {
					// try to find benchmark entry points.
					Set<Entrypoint> benchmarkEntryPoints = Util.findJavaFXEntryPoints(engine.getClassHierarchy());

					// add them as well.
					addImplicitEntryPoints(entryPoints, benchmarkEntryPoints);
				}
			}

			if (entryPoints.isEmpty()) {
				LOGGER.warning(() -> "Project: " + engine.getProject().getElementName() + " has no entry points.");
				return entryPoints;
			}

			collector.ifPresent(TimeCollector::stop);

			// set options.
			AnalysisOptions options = engine.getDefaultOptions(entryPoints);
			// Turn off reflection analysis.
			options.setReflectionOptions(ReflectionOptions.NONE);
			options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());

			try {
				engine.buildSafeCallGraph(options, SubMonitor.convert(monitor, "Building call graph", 1));
			} catch (IllegalStateException e) {
				LOGGER.log(Level.SEVERE, e, () -> "Exception encountered while building call graph for project: "
						+ engine.getProject().getElementName());
				throw e;
			}
			// TODO: Can I slice the graph so that only nodes relevant to the
			// instance in question are present?
			this.enginesWithBuiltCallGraphsToEntrypointsUsed.put(engine, entryPoints);
		}
		return this.enginesWithBuiltCallGraphsToEntrypointsUsed.get(engine);
	}

	public int getNForStreams() {
		return this.nForStreams;
	}

	public int getNumberOfProcessedStreamInstances() {
		return this.numberOfProcessedStreamInstances;
	}

	public int getNumberOfSkippedStreamInstances() {
		return this.numberOfSkippedStreamInstances;
	}

	public Set<Stream> getStreamSet() {
		return this.streamSet;
	}

	public void setFindImplicitBenchmarkEntryPoints(boolean findImplicitBenchmarkEntryPoints) {
		this.findImplicitBenchmarkEntryPoints = findImplicitBenchmarkEntryPoints;
	}

	public void setFindImplicitEntryPoints(boolean findImplicitEntryPoints) {
		this.findImplicitEntryPoints = findImplicitEntryPoints;
	}

	public void setFindImplicitJavaFXEntryPoints(boolean findImplicitJavaFXEntryPoints) {
		this.findImplicitJavaFXEntryPoints = findImplicitJavaFXEntryPoints;
	}

	public void setFindImplicitTestEntryPoints(boolean findImplicitTestEntryPoints) {
		this.findImplicitTestEntryPoints = findImplicitTestEntryPoints;
	}

	protected void setNForStreams(int nForStreams) {
		this.nForStreams = nForStreams;
	}

	protected void setNumberOfProcessedStreamInstances(int numberOfProcessedStreamInstances) {
		this.numberOfProcessedStreamInstances = numberOfProcessedStreamInstances;
	}

	protected void setNumberOfSkippedStreamInstances(int numberOfSkippedStreamInstances) {
		this.numberOfSkippedStreamInstances = numberOfSkippedStreamInstances;
	}

	public boolean shouldFindImplicitBenchmarkEntryPoints() {
		return this.findImplicitBenchmarkEntryPoints;
	}

	public boolean shouldFindImplicitEntryPoints() {
		return this.findImplicitEntryPoints;
	}

	public boolean shouldFindImplicitJavaFXEntryPoints() {
		return this.findImplicitJavaFXEntryPoints;
	}

	public boolean shouldFindImplicitTestEntryPoints() {
		return this.findImplicitTestEntryPoints;
	}

	/**
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.MethodInvocation)
	 */
	@Override
	public boolean visit(MethodInvocation node) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		ITypeBinding returnType = methodBinding.getReturnType();
		boolean returnTypeImplementsBaseStream = Util.implementsBaseStream(returnType);

		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		boolean declaringClassImplementsBaseStream = Util.implementsBaseStream(declaringClass);

		// Try to limit the analyzed methods to those of the API. In other
		// words, don't process methods returning streams that are declared in
		// the client application. TODO: This could be problematic if the API
		// implementation treats itself as a "client."
		String[] declaringClassPackageNameComponents = declaringClass.getPackage().getNameComponents();
		boolean isFromAPI = declaringClassPackageNameComponents.length > 0
				&& declaringClassPackageNameComponents[0].equals("java");

		boolean instanceMethod = !JdtFlags.isStatic(methodBinding);
		boolean intermediateOperation = instanceMethod && declaringClassImplementsBaseStream;

		// java.util.stream.BaseStream is the top-level interface for all
		// streams. Make sure we don't include intermediate operations.
		if (returnTypeImplementsBaseStream && !intermediateOperation && isFromAPI) {
			Stream stream = null;
			try {
				stream = new Stream(node);
			} catch (ClassHierarchyException | IOException | CoreException | InvalidClassFileException
					| CancelException e) {
				LOGGER.log(Level.SEVERE, "Encountered exception while processing: " + node, e);
				throw new RuntimeException(e);
			}
			this.getStreamSet().add(stream);
		}

		return super.visit(node);
	}

	@Override
	public boolean visit(MethodDeclaration methodDeclaration) {
		IMethodBinding methodBinding = methodDeclaration.resolveBinding();
		ITypeBinding returnType = methodBinding.getReturnType();
		boolean returnTypeImplementsBaseStream = Util.implementsBaseStream(returnType);
		if (returnTypeImplementsBaseStream) {
			numberOfMethodForStreamReturnType++;
			super.visit(methodDeclaration);
		}
		ITypeBinding parameterTypes[] = methodBinding.getParameterTypes();
		if (parameterTypes.length < 1)
			return false;
		else {
			for (ITypeBinding parameterBinding : parameterTypes) {
				if (Util.implementsBaseStream(parameterBinding)) {
					numberOfMethodForStreamParameter++;
					break;
				}
			}
		}
		return super.visit(methodDeclaration);
	}

	public int getNumberOfMethodForStreamReturnType() {
		return this.numberOfMethodForStreamReturnType;
	}

	public int getNumberMethodForStreamParameter() {
		return this.numberOfMethodForStreamParameter;
	}
}
