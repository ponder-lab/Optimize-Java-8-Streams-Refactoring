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
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.rules.TypestateRule;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.traverse.DFS;
import com.ibm.wala.util.scope.JUnitEntryPoints;

import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;
import edu.cuny.hunter.streamrefactoring.core.utils.TimeCollector;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

@SuppressWarnings("restriction")
public class StreamAnalyzer extends ASTVisitor {

	private static final String ENTRY_POINT_FILENAME = "entry_points.txt";

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	private static final int N_FOR_STREAMS_DEFAULT = 2;

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
	 * @return A {@link Map} of project's analyzed along with the associated
	 *         {@link ProjectAnalysisResult}
	 * @see #analyze(Optional).
	 */
	public Map<IJavaProject, ProjectAnalysisResult> analyze() throws CoreException {
		return this.analyze(Optional.empty());
	}

	/**
	 * Analyzes this {@link StreamAnalyzer}'s streams.
	 *
	 * @param collector
	 *            To exclude from the time certain parts of the analysis.
	 * @return A {@link Map} of project's analyzed along with the associated
	 *         {@link ProjectAnalysisResult}
	 * @see #analyze().
	 */
	public Map<IJavaProject, ProjectAnalysisResult> analyze(Optional<TimeCollector> collector) throws CoreException {
		LOGGER.fine(() -> "Using N = " + this.getNForStreams() + ".");

		Map<IJavaProject, ProjectAnalysisResult> ret = new HashMap<>();

		// collect the projects to be analyzed.
		Map<IJavaProject, Set<Stream>> projectToStreams = this.getStreamSet().stream().filter(s -> s.getStatus().isOK())
				.collect(Collectors.groupingBy(Stream::getCreationJavaProject, Collectors.toSet()));

		// process each project.
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

			Collection<Entrypoint> usedEntryPoints = null;
			Collection<CGNode> deadEntryPoints = new HashSet<>();
			try {
				// build the call graph for the project,
				// also get a set of used entry points.
				usedEntryPoints = this.buildCallGraph(engine, collector);

				if (!usedEntryPoints.isEmpty()) {
					deadEntryPoints = discoverDeadEntryPoints(engine);
					// rebuild the call graph

					// we do not need to prune dead entry points here
				}

			} catch (IOException | CoreException | CancelException e) {
				LOGGER.log(Level.SEVERE,
						"Exception encountered while building call graph for: " + project.getElementName() + ".", e);
				throw new RuntimeException(e);
			}

			// save the project analysis result
			ProjectAnalysisResult projectAnalysisResult = new ProjectAnalysisResult(usedEntryPoints, deadEntryPoints);

			// save the entry points.
			ret.put(project, projectAnalysisResult);

			if (projectAnalysisResult.getUsedEntryPoints().isEmpty()) {
				// add a status entry for each stream in the project
				for (Stream stream : projectToStreams.get(project))
					stream.addStatusEntry(PreconditionFailure.NO_ENTRY_POINT,
							"Project: " + engine.getProject().getElementName() + " has no entry points.");
				return ret;
			}

			// delete codes here
			// because we only need codes to print dead entry points
			
		} // end for each stream.
		return ret;
	}

	/**
	 * Get a set of pruned entry points.
	 * 
	 * @param deadEntryPoints
	 *            A collection of dead entry points.
	 * @param usedEntryPoints
	 *            A collection of entry points used to build the first call graph.
	 * @return A collection of entry points.
	 */
	protected static Collection<Entrypoint> getPrunedEntryPoints(Collection<CGNode> deadEntryPoints,
			Collection<Entrypoint> usedEntryPoints) {
		Collection<Entrypoint> deadEntryPointCollection = new HashSet<>();
		usedEntryPoints.forEach(e -> {
			// check whether the used entry point
			// is in the set of dead entry point
			Iterator<CGNode> deadEntryPointIterator = deadEntryPoints.iterator();

			while (deadEntryPointIterator.hasNext()) {
				CGNode deadEntryPoint = deadEntryPointIterator.next();
				if (e.getMethod().equals(deadEntryPoint.getMethod())) {
					deadEntryPointCollection.add(e);
					break;
				}
			}
		});
		usedEntryPoints.removeAll(deadEntryPointCollection);
		return usedEntryPoints;
	}

	/**
	 * Discover a set of entry points used to build the {@link CallGraph}.
	 * 
	 * @param engine
	 *            The EclipseProjectAnalysisEngine for which to build the call
	 *            graph.
	 * @return The {@link Entrypoint}s used in building the {@link CallGraph}.
	 */
	protected Set<Entrypoint> discoverEntryPoints(EclipseProjectAnalysisEngine<InstanceKey> engine) throws IOException {
		Set<Entrypoint> entryPoints;

		// find the entry_points.txt in the project directory
		File entryPointFile = getEntryPointsFile(engine.getProject().getResource().getLocation(), ENTRY_POINT_FILENAME);

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

		return entryPoints;

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
			Optional<TimeCollector> collector)
			throws IOException, CoreException, CallGraphBuilderCancelException, CancelException {
		// if we haven't built the call graph yet.
		if (!this.enginesWithBuiltCallGraphsToEntrypointsUsed.keySet().contains(engine)) {
			// find entry points (but exclude it from the time).
			collector.ifPresent(TimeCollector::start);
			Set<Entrypoint> entryPoints = discoverEntryPoints(engine);
			collector.ifPresent(TimeCollector::stop);

			if (entryPoints.isEmpty()) {
				LOGGER.warning(() -> "Project: " + engine.getProject().getElementName() + " has no entry points.");
				return entryPoints;
			}

			buildCallGraphFromEntryPoints(engine, entryPoints);
		}
		// get the project analysis result
		return this.enginesWithBuiltCallGraphsToEntrypointsUsed.get(engine);
	}

	/**
	 * Given entry points, builds the call graph that is part of the
	 * {@link EclipseProjectAnalysisEngine}.
	 *
	 * @param engine
	 *            The EclipseProjectAnalysisEngine for which to build the call
	 *            graph.
	 * @param collection
	 *            The {@link entryPoints} used in building the {@link CallGraph}.
	 */
	protected void buildCallGraphFromEntryPoints(EclipseProjectAnalysisEngine<InstanceKey> engine,
			Collection<Entrypoint> collection) throws CallGraphBuilderCancelException, CancelException {

		// set options.
		AnalysisOptions options = engine.getDefaultOptions(collection);
		// Turn off reflection analysis.
		options.setReflectionOptions(ReflectionOptions.NONE);
		options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());

		try {
			engine.buildSafeCallGraph(options);
		} catch (IllegalStateException e) {
			LOGGER.log(Level.SEVERE, e, () -> "Exception encountered while building call graph for project: "
					+ engine.getProject().getElementName());
			throw e;
		}

		// TODO: Can I slice the graph so that only nodes relevant to the
		// instance in question are present?
		this.enginesWithBuiltCallGraphsToEntrypointsUsed.put(engine, collection);

	}

	/**
	 * Discover dead entry points.
	 * 
	 * @param engine
	 *            An {@link EclipseProjectAnalysisEngine}.
	 * @return a collection of dead entry points.
	 */
	private Collection<CGNode> discoverDeadEntryPoints(EclipseProjectAnalysisEngine<InstanceKey> engine) {
		CallGraph callGraph = engine.getCallGraph();

		// get a set of entry point nodes
		Collection<CGNode> entryPointNodes = callGraph.getEntrypointNodes();

		Set<CGNode> streamNodes = getStreamCreationNodes(this.getStreamSet().iterator(), engine);

		Set<CGNode> deadEntryPoints = getDeadEntryPointNodes(entryPointNodes, streamNodes, callGraph);

		deadEntryPoints.forEach(e -> {
			LOGGER.info(() -> "Discover dead entry point: " + e.getMethod().getSignature());
		});

		return deadEntryPoints;
	}

	/**
	 * Get a set of stream creation method nodes.
	 * 
	 * @param streamIterator
	 *            An iterator of {@link Stream}s.
	 * @param engine
	 *            An {@link EclipseProjectAnalysisEngine}.
	 * @return a set of {@link CGNode}s for stream creation methods.
	 */
	private Set<CGNode> getStreamCreationNodes(Iterator<Stream> streamIterator,
			EclipseProjectAnalysisEngine<InstanceKey> engine) {
		Set<CGNode> streamNodes = new HashSet<>();
		while (streamIterator.hasNext()) {
			Stream stream = streamIterator.next();
			try {
				streamNodes.addAll(stream.getEnclosingMethodNodes(engine));
			} catch (NoEnclosingMethodNodeFoundException e) {
				LOGGER.log(Level.WARNING, "Exception encountered while get a node for the enclosing method.", e);
			}

		}
		return streamNodes;
	}

	/**
	 * Get all possible dead entry point nodes.
	 * 
	 * @param entryPointNodes
	 *            collection of entry point {@link CGNode}s.
	 * @param streamNodes
	 *            a set of stream creation nodes.
	 * @param callGraph
	 *            a {@link CallGraph}.
	 * @return a set of dead entry points.
	 */
	private static Set<CGNode> getDeadEntryPointNodes(Collection<CGNode> entryPointNodes, Set<CGNode> streamNodes,
			CallGraph callGraph) {
		Set<CGNode> deadEntryPoints = new HashSet<>();
		Set<IClass> aliveClass = new HashSet<>();
		Set<CGNode> ctorsOrStaticInitializerNodes = new HashSet<>();
		for (CGNode entryPointNode : entryPointNodes) {
			// We will process ctors and static initializers later
			if (Util.isCtor(entryPointNode) || Util.isStaticInitializer(entryPointNode)) {
				ctorsOrStaticInitializerNodes.add(entryPointNode);
				continue;
			}

			if (!isReachable(entryPointNode, streamNodes, callGraph))
				deadEntryPoints.add(entryPointNode);
			else
				aliveClass.add(entryPointNode.getMethod().getDeclaringClass());
		}
		// the ctors and static initializer nodes should be in the set of alive
		// nodes if at least one entry point is alive in its class
		for (CGNode entryPointNode : ctorsOrStaticInitializerNodes)
			if (!aliveClass.contains(entryPointNode.getMethod().getDeclaringClass()))
				deadEntryPoints.add(entryPointNode);
		return deadEntryPoints;
	}

	/**
	 * Given an entry point node and a set of steam creation nodes, check whether
	 * exists an stream creation node which is reachable from the entry point
	 * 
	 * @param entryPointNode
	 *            a {@CGNode}
	 * @param streamNodes
	 *            a set of stream creation nodes.
	 * @param callGraph
	 * @return true: reachable; false: unreachable
	 */
	private static boolean isReachable(CGNode entryPointNode, Collection<CGNode> streamNodes, CallGraph callGraph) {
		// get a set of start nodes for DFS
		Set<CGNode> singleEntryPointNode = new HashSet<>();
		singleEntryPointNode.add(entryPointNode);

		// get all reachable nodes from the entry point node
		final Set<CGNode> reachableNodes = DFS.getReachableNodes(callGraph, singleEntryPointNode);

		for (CGNode reachableNode : reachableNodes)
			if (streamNodes.contains(reachableNode))
				return true;
		return false;
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
}
