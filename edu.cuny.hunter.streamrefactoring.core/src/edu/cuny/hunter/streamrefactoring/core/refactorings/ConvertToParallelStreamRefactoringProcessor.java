package edu.cuny.hunter.streamrefactoring.core.refactorings;

import static org.eclipse.jdt.ui.JavaElementLabels.ALL_FULLY_QUALIFIED;
import static org.eclipse.jdt.ui.JavaElementLabels.getElementLabel;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditBasedChangeManager;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;
import org.osgi.framework.FrameworkUtil;

import com.ibm.wala.ipa.callgraph.Entrypoint;

import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionFailure;
import edu.cuny.hunter.streamrefactoring.core.analysis.ProjectAnalysisResult;
import edu.cuny.hunter.streamrefactoring.core.analysis.Stream;
import edu.cuny.hunter.streamrefactoring.core.analysis.StreamAnalyzer;
import edu.cuny.hunter.streamrefactoring.core.descriptors.ConvertStreamToParallelRefactoringDescriptor;
import edu.cuny.hunter.streamrefactoring.core.messages.Messages;
import edu.cuny.hunter.streamrefactoring.core.utils.TimeCollector;

/**
 * The activator class controls the plug-in life cycle
 *
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi
 *         Khatchadourian</a>
 */
@SuppressWarnings({ "restriction" })
public class ConvertToParallelStreamRefactoringProcessor extends RefactoringProcessor {

	// private Set<IMethod> unmigratableMethods = new
	// UnmigratableMethodSet(sourceMethods);

	/**
	 * The minimum logging level, one of the constants in
	 * org.eclipse.core.runtime.IStatus.
	 */
	private static int loggingLevel = IStatus.WARNING;

	private static final int N_FOR_STREAMS_DEFAULT = 2;

	@SuppressWarnings("unused")
	private static final GroupCategorySet SET_CONVERT_STREAM_TO_PARALLEL = new GroupCategorySet(
			new GroupCategory("edu.cuny.hunter.streamrefactoring", //$NON-NLS-1$
					Messages.CategoryName, Messages.CategoryDescription));

	protected static int getLoggingLevel() {
		return loggingLevel;
	}

	private static void log(int severity, String message) {
		if (severity >= getLoggingLevel()) {
			String name = FrameworkUtil.getBundle(ConvertToParallelStreamRefactoringProcessor.class).getSymbolicName();
			IStatus status = new Status(severity, name, message);
			JavaPlugin.log(status);
		}
	}

	private static void logWarning(String message) {
		log(IStatus.WARNING, message);
	}

	/**
	 * Minimum logging level. One of the constants in
	 * org.eclipse.core.runtime.IStatus.
	 *
	 * @param level
	 *            The minimum logging level to set.
	 * @see org.eclipse.core.runtime.IStatus.
	 */
	public static void setLoggingLevel(int level) {
		loggingLevel = level;
	}

	private Map<ICompilationUnit, CompilationUnitRewrite> compilationUnitToCompilationUnitRewriteMap = new HashMap<>();

	/**
	 * For excluding AST parse time.
	 */
	private TimeCollector excludedTimeCollector = new TimeCollector();

	private IJavaProject[] javaProjects;

	/** Does the refactoring use a working copy layer? */
	private final boolean layer;

	private int nForStreams = N_FOR_STREAMS_DEFAULT;

	private int numberOfProcessedStreamInstances;

	private int numberOfSkippedStreamInstances;

	private Map<IJavaProject, ProjectAnalysisResult> projectToEntryPoints;

	private SearchEngine searchEngine = new SearchEngine();

	/** The code generation settings, or <code>null</code> */
	private CodeGenerationSettings settings;

	private Set<Stream> streamSet;

	private Map<ITypeRoot, CompilationUnit> typeRootToCompilationUnitMap = new HashMap<>();

	private Map<IType, ITypeHierarchy> typeToTypeHierarchyMap = new HashMap<>();

	private boolean useImplicitBenchmarkEntrypoints = false;

	private boolean useImplicitEntrypoints = true;

	private boolean useImplicitJavaFXEntrypoints = false;

	private boolean useImplicitTestEntrypoints = false;

	public ConvertToParallelStreamRefactoringProcessor() throws JavaModelException {
		this(null, null, false, true, false, false, false, Optional.empty());
	}

	public ConvertToParallelStreamRefactoringProcessor(final CodeGenerationSettings settings,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(null, settings, false, true, false, false, false, monitor);
	}

	public ConvertToParallelStreamRefactoringProcessor(IJavaProject[] javaProjects,
			final CodeGenerationSettings settings, boolean layer, boolean useImplicitEntrypoints,
			boolean useImplicitTestEntrypoints, boolean useImplicitBenchmarkEntrypoints,
			boolean useImplicitJavaFXEntrypoints, Optional<IProgressMonitor> monitor) throws JavaModelException {
		try {
			this.javaProjects = javaProjects;
			this.settings = settings;
			this.layer = layer;
			this.useImplicitEntrypoints = useImplicitEntrypoints;
			this.useImplicitTestEntrypoints = useImplicitTestEntrypoints;
			this.useImplicitBenchmarkEntrypoints = useImplicitBenchmarkEntrypoints;
			this.useImplicitJavaFXEntrypoints = useImplicitJavaFXEntrypoints;
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	public ConvertToParallelStreamRefactoringProcessor(IJavaProject[] javaProjects,
			final CodeGenerationSettings settings, boolean layer, int nForStreams, boolean useImplicitEntrypoints,
			boolean useImplicitTestEntrypoints, boolean useImplicitBenchmarkEntrypoints,
			boolean useImplicitJavaFXEntrypoints, Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(javaProjects, settings, layer, useImplicitEntrypoints, useImplicitTestEntrypoints,
				useImplicitBenchmarkEntrypoints, useImplicitJavaFXEntrypoints, monitor);
		this.nForStreams = nForStreams;
	}

	public ConvertToParallelStreamRefactoringProcessor(IJavaProject[] javaProjects,
			final CodeGenerationSettings settings, boolean useImplicitJoinpoints, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		this(javaProjects, settings, false, useImplicitJoinpoints, false, false, false, monitor);
	}

	public ConvertToParallelStreamRefactoringProcessor(IJavaProject[] javaProjects,
			final CodeGenerationSettings settings, int nForStreams, boolean useImplicitJoinpoints,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(javaProjects, settings, false, nForStreams, useImplicitJoinpoints, false, false, false, monitor);
	}

	public ConvertToParallelStreamRefactoringProcessor(IJavaProject[] javaProjects,
			final CodeGenerationSettings settings, Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(javaProjects, settings, false, true, false, false, false, monitor);
	}

	public ConvertToParallelStreamRefactoringProcessor(Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(null, null, false, true, false, false, false, monitor);
	}

	private RefactoringStatus checkExistence(IMember member, PreconditionFailure failure) {
		// if (member == null || !member.exists()) {
		// return createError(failure, member);
		// }
		return new RefactoringStatus();
	}

	@Override
	public RefactoringStatus checkFinalConditions(final IProgressMonitor monitor, final CheckConditionsContext context)
			throws CoreException, OperationCanceledException {
		try {
			SubMonitor subMonitor = SubMonitor.convert(monitor, Messages.CheckingPreconditions,
					this.getJavaProjects().length * 1000);
			final RefactoringStatus status = new RefactoringStatus();
			StreamAnalyzer analyzer = new StreamAnalyzer(false, this.getNForStreams(), this.getUseImplicitEntrypoints(),
					this.getUseImplicitTestEntrypoints(), this.getUseImplicitBenchmarkEntrypoints(),
					this.getUseImplicitJavaFXEntrypoints());
			this.setStreamSet(analyzer.getStreamSet());

			for (IJavaProject jproj : this.getJavaProjects()) {
				IPackageFragmentRoot[] roots = jproj.getPackageFragmentRoots();
				for (IPackageFragmentRoot root : roots) {
					IJavaElement[] children = root.getChildren();
					for (IJavaElement child : children)
						if (child.getElementType() == IJavaElement.PACKAGE_FRAGMENT) {
							IPackageFragment fragment = (IPackageFragment) child;
							ICompilationUnit[] units = fragment.getCompilationUnits();
							for (ICompilationUnit unit : units) {
								CompilationUnit compilationUnit = this.getCompilationUnit(unit, subMonitor.split(1));
								compilationUnit.accept(analyzer);
							}
						}
				}
			}

			// analyze and set entry points.
			this.projectToEntryPoints = analyzer.analyze(Optional.of(this.getExcludedTimeCollector()));

			// set statistics for stream instances.
			this.setNumberOfProcessedStreamInstances(analyzer.getNumberOfProcessedStreamInstances());
			this.setNumberOfSkippedStreamInstances(analyzer.getNumberOfSkippedStreamInstances());

			// map empty set to unprocessed projects.
			for (IJavaProject project : this.getJavaProjects())
				this.projectToEntryPoints.computeIfAbsent(project, p -> new ProjectAnalysisResult());

			// get the status of each stream.
			RefactoringStatus collectedStatus = this.getStreamSet().stream().map(Stream::getStatus)
					.collect(() -> new RefactoringStatus(), (a, b) -> a.merge(b), (a, b) -> a.merge(b));
			status.merge(collectedStatus);

			// if there are no fatal errors.
			if (!status.hasFatalError()) {
				// these are the streams passing preconditions.
				Set<Stream> passingStreamSet = this.getOptimizableStreams();

				// add a fatal error if there are no passing streams.
				if (passingStreamSet.isEmpty())
					status.addFatalError(Messages.NoStreamsHavePassedThePreconditions);
				else {
					// TODO:
					// Checks.addModifiedFilesToChecker(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits()),
					// context);
				}
			}
			return status;
		} catch (Exception e) {
			JavaPlugin.log(e);
			throw e;
		} finally {
			monitor.done();
		}
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		try {
			this.clearCaches();
			this.getExcludedTimeCollector().clear();

			// if (this.getSourceMethods().isEmpty())
			// return
			// RefactoringStatus.createFatalErrorStatus(Messages.StreamsNotSpecified);
			// else {
			RefactoringStatus status = new RefactoringStatus();
			pm.beginTask(Messages.CheckingPreconditions, 1);
			return status;
			// }
		} catch (Exception e) {
			JavaPlugin.log(e);
			throw e;
		} finally {
			pm.done();
		}
	}

	private RefactoringStatus checkProjectCompliance(IJavaProject destinationProject) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();

		// if (!JavaModelUtil.is18OrHigher(destinationProject))
		// addErrorAndMark(status,
		// PreconditionFailure.DestinationProjectIncompatible, sourceMethod,
		// targetMethod);

		return status;
	}

	private RefactoringStatus checkStructure(IMember member) throws JavaModelException {
		if (!member.isStructureKnown())
			return RefactoringStatus.createErrorStatus(
					MessageFormat.format(Messages.CUContainsCompileErrors, getElementLabel(member, ALL_FULLY_QUALIFIED),
							getElementLabel(member.getCompilationUnit(), ALL_FULLY_QUALIFIED)),
					JavaStatusContext.create(member.getCompilationUnit()));
		return new RefactoringStatus();
	}

	private RefactoringStatus checkWritabilitiy(IMember member, PreconditionFailure failure) {
		// if (member.isBinary() || member.isReadOnly()) {
		// return createError(failure, member);
		// }
		return new RefactoringStatus();
	}

	public void clearCaches() {
		this.getTypeToTypeHierarchyMap().clear();
		this.getCompilationUnitToCompilationUnitRewriteMap().clear();
		this.getTypeRootToCompilationUnitMap().clear();
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		try {
			pm.beginTask(Messages.CreatingChange, 1);

			final TextEditBasedChangeManager manager = new TextEditBasedChangeManager();

			// save the source changes.
			ICompilationUnit[] units = this.getCompilationUnitToCompilationUnitRewriteMap().keySet().stream()
					.filter(cu -> !manager.containsChangesIn(cu)).toArray(ICompilationUnit[]::new);

			for (ICompilationUnit cu : units) {
				CompilationUnit compilationUnit = this.getCompilationUnit(cu, pm);
				this.manageCompilationUnit(manager, this.getCompilationUnitRewrite(cu, compilationUnit),
						Optional.of(new SubProgressMonitor(pm, IProgressMonitor.UNKNOWN)));
			}

			final Map<String, String> arguments = new HashMap<>();
			int flags = RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE;

			// TODO: Fill in description.

			ConvertStreamToParallelRefactoringDescriptor descriptor = new ConvertStreamToParallelRefactoringDescriptor(
					null, "TODO", null, arguments, flags);

			return new DynamicValidationRefactoringChange(descriptor, this.getProcessorName(), manager.getAllChanges());
		} finally {
			pm.done();
			this.clearCaches();
		}
	}

	/**
	 * Creates a working copy layer if necessary.
	 *
	 * @param monitor
	 *            the progress monitor to use
	 * @return a status describing the outcome of the operation
	 */
	private RefactoringStatus createWorkingCopyLayer(IProgressMonitor monitor) {
		try {
			monitor.beginTask(Messages.CheckingPreconditions, 1);
			// TODO ICompilationUnit unit =
			// getDeclaringType().getCompilationUnit();
			// if (fLayer)
			// unit = unit.findWorkingCopy(fOwner);
			// resetWorkingCopies(unit);
			return new RefactoringStatus();
		} finally {
			monitor.done();
		}
	}

	private CompilationUnit getCompilationUnit(ITypeRoot root, IProgressMonitor pm) {
		CompilationUnit compilationUnit = this.getTypeRootToCompilationUnitMap().get(root);
		if (compilationUnit == null) {
			this.getExcludedTimeCollector().start();
			compilationUnit = RefactoringASTParser.parseWithASTProvider(root, true, pm);
			this.getExcludedTimeCollector().stop();
			this.getTypeRootToCompilationUnitMap().put(root, compilationUnit);
		}
		return compilationUnit;
	}

	private CompilationUnitRewrite getCompilationUnitRewrite(ICompilationUnit unit, CompilationUnit root) {
		CompilationUnitRewrite rewrite = this.getCompilationUnitToCompilationUnitRewriteMap().get(unit);
		if (rewrite == null) {
			rewrite = new CompilationUnitRewrite(unit, root);
			this.getCompilationUnitToCompilationUnitRewriteMap().put(unit, rewrite);
		}
		return rewrite;
	}

	protected Map<ICompilationUnit, CompilationUnitRewrite> getCompilationUnitToCompilationUnitRewriteMap() {
		return this.compilationUnitToCompilationUnitRewriteMap;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] getElements() {
		return null;
	}

	public ProjectAnalysisResult getEntryPoints(IJavaProject javaProject) {
		return this.projectToEntryPoints.get(javaProject);
	}

	public TimeCollector getExcludedTimeCollector() {
		return this.excludedTimeCollector;
	}

	@Override
	public String getIdentifier() {
		return ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID;
	}

	protected IJavaProject[] getJavaProjects() {
		return this.javaProjects;
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

	public Set<Stream> getOptimizableStreams() {
		return this.getStreamSet().parallelStream().filter(s -> !s.getStatus().hasError()).collect(Collectors.toSet());
	}

	@Override
	public String getProcessorName() {
		return Messages.Name;
	}

	private SearchEngine getSearchEngine() {
		return this.searchEngine;
	}

	public Set<Stream> getStreamSet() {
		return this.streamSet;
	}

	private ITypeHierarchy getTypeHierarchy(IType type, Optional<IProgressMonitor> monitor) throws JavaModelException {
		try {
			ITypeHierarchy ret = this.getTypeToTypeHierarchyMap().get(type);

			if (ret == null) {
				ret = type.newTypeHierarchy(monitor.orElseGet(NullProgressMonitor::new));
				this.getTypeToTypeHierarchyMap().put(type, ret);
			}

			return ret;
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	protected Map<ITypeRoot, CompilationUnit> getTypeRootToCompilationUnitMap() {
		return this.typeRootToCompilationUnitMap;
	}

	protected Map<IType, ITypeHierarchy> getTypeToTypeHierarchyMap() {
		return this.typeToTypeHierarchyMap;
	}

	public Set<Stream> getUnoptimizableStreams() {
		return this.getStreamSet().parallelStream().filter(s -> s.getStatus().hasError()).collect(Collectors.toSet());
	}

	private boolean getUseImplicitBenchmarkEntrypoints() {
		return this.useImplicitBenchmarkEntrypoints;
	}

	private boolean getUseImplicitEntrypoints() {
		return this.useImplicitEntrypoints;
	}

	private boolean getUseImplicitJavaFXEntrypoints() {
		return this.useImplicitJavaFXEntrypoints;
	}

	private boolean getUseImplicitTestEntrypoints() {
		return this.useImplicitTestEntrypoints;
	}

	@Override
	public boolean isApplicable() throws CoreException {
		// return
		// RefactoringAvailabilityTester.isInterfaceMigrationAvailable(getSourceMethods().parallelStream()
		// .filter(m ->
		// !this.getUnmigratableMethods().contains(m)).toArray(IMethod[]::new),
		// Optional.empty());
		return true;
	}

	@Override
	public RefactoringParticipant[] loadParticipants(RefactoringStatus status, SharableParticipants sharedParticipants)
			throws CoreException {
		return new RefactoringParticipant[0];
	}

	private void logInfo(String message) {
		log(IStatus.INFO, message);
	}

	private void manageCompilationUnit(final TextEditBasedChangeManager manager, CompilationUnitRewrite rewrite,
			Optional<IProgressMonitor> monitor) throws CoreException {
		monitor.ifPresent(m -> m.beginTask("Creating change ...", IProgressMonitor.UNKNOWN));
		CompilationUnitChange change = rewrite.createChange(false, monitor.orElseGet(NullProgressMonitor::new));

		if (change != null)
			change.setTextType("java");

		manager.manage(rewrite.getCu(), change);
	}

	public void setNForStreams(int nForStreams) {
		this.nForStreams = nForStreams;
	}

	protected void setNumberOfProcessedStreamInstances(int numberOfProcessedStreamInstances) {
		this.numberOfProcessedStreamInstances = numberOfProcessedStreamInstances;
	}

	protected void setNumberOfSkippedStreamInstances(int numberOfSkippedStreamInstances) {
		this.numberOfSkippedStreamInstances = numberOfSkippedStreamInstances;
	}

	protected void setStreamSet(Set<Stream> streamSet) {
		this.streamSet = streamSet;
	}
}
