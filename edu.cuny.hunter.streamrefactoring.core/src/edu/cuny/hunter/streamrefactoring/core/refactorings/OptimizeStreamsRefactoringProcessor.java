package edu.cuny.hunter.streamrefactoring.core.refactorings;

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
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditBasedChangeManager;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.osgi.framework.FrameworkUtil;

import com.ibm.wala.ipa.callgraph.Entrypoint;

import edu.cuny.citytech.refactoring.common.core.RefactoringProcessor;
import edu.cuny.hunter.streamrefactoring.core.analysis.Stream;
import edu.cuny.hunter.streamrefactoring.core.analysis.StreamAnalyzer;
import edu.cuny.hunter.streamrefactoring.core.descriptors.OptimizeStreamRefactoringDescriptor;
import edu.cuny.hunter.streamrefactoring.core.messages.Messages;

/**
 * The activator class controls the plug-in life cycle
 *
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi
 *         Khatchadourian</a>
 */
@SuppressWarnings({ "restriction", "deprecation" })
public class OptimizeStreamsRefactoringProcessor extends RefactoringProcessor {

	private static final int N_FOR_STREAMS_DEFAULT = 2;

	@SuppressWarnings("unused")
	private static final GroupCategorySet SET_OPTIMIZE_STREAMS = new GroupCategorySet(
			new GroupCategory("edu.cuny.hunter.streamrefactoring", //$NON-NLS-1$
					Messages.CategoryName, Messages.CategoryDescription));

	private static void log(int severity, String message) {
		if (severity >= getLoggingLevel()) {
			String name = FrameworkUtil.getBundle(OptimizeStreamsRefactoringProcessor.class).getSymbolicName();
			IStatus status = new Status(severity, name, message);
			JavaPlugin.log(status);
		}
	}

	@SuppressWarnings("unused")
	private static void logWarning(String message) {
		log(IStatus.WARNING, message);
	}

	private IJavaProject[] javaProjects;

	private int nForStreams = N_FOR_STREAMS_DEFAULT;

	private int numberOfProcessedStreamInstances;

	private int numberOfSkippedStreamInstances;

	private Map<IJavaProject, Collection<Entrypoint>> projectToEntryPoints;

	private Set<Stream> streamSet;

	private boolean useImplicitBenchmarkEntrypoints;

	private boolean useImplicitEntrypoints = true;

	private boolean useImplicitJavaFXEntrypoints;

	private boolean useImplicitTestEntrypoints;

	public OptimizeStreamsRefactoringProcessor() throws JavaModelException {
		this(null, null, false, true, false, false, false, Optional.empty());
	}

	public OptimizeStreamsRefactoringProcessor(final CodeGenerationSettings settings,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(null, settings, false, true, false, false, false, monitor);
	}

	public OptimizeStreamsRefactoringProcessor(IJavaProject[] javaProjects, final CodeGenerationSettings settings,
			boolean layer, boolean useImplicitEntrypoints, boolean useImplicitTestEntrypoints,
			boolean useImplicitBenchmarkEntrypoints, boolean useImplicitJavaFXEntrypoints,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
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

	public OptimizeStreamsRefactoringProcessor(IJavaProject[] javaProjects, final CodeGenerationSettings settings,
			boolean layer, int nForStreams, boolean useImplicitEntrypoints, boolean useImplicitTestEntrypoints,
			boolean useImplicitBenchmarkEntrypoints, boolean useImplicitJavaFXEntrypoints,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(javaProjects, settings, layer, useImplicitEntrypoints, useImplicitTestEntrypoints,
				useImplicitBenchmarkEntrypoints, useImplicitJavaFXEntrypoints, monitor);
		this.nForStreams = nForStreams;
	}

	public OptimizeStreamsRefactoringProcessor(IJavaProject[] javaProjects, final CodeGenerationSettings settings,
			boolean useImplicitJoinpoints, Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(javaProjects, settings, false, useImplicitJoinpoints, false, false, false, monitor);
	}

	public OptimizeStreamsRefactoringProcessor(IJavaProject[] javaProjects, final CodeGenerationSettings settings,
			int nForStreams, boolean useImplicitJoinpoints, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		this(javaProjects, settings, false, nForStreams, useImplicitJoinpoints, false, false, false, monitor);
	}

	public OptimizeStreamsRefactoringProcessor(IJavaProject[] javaProjects, final CodeGenerationSettings settings,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(javaProjects, settings, false, true, false, false, false, monitor);
	}

	public OptimizeStreamsRefactoringProcessor(Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(null, null, false, true, false, false, false, monitor);
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
				this.projectToEntryPoints.computeIfAbsent(project, p -> Collections.emptySet());

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
			RefactoringStatus status = super.checkInitialConditions(pm);

			if (this.getJavaProjects().length == 0)
				status.merge(RefactoringStatus.createFatalErrorStatus(Messages.StreamsNotSpecified));

			return status;
		} catch (Exception e) {
			JavaPlugin.log(e);
			throw e;
		} finally {
			pm.done();
		}
	}

	@Override
	protected RefactoringStatus checkStructure(IMember member) throws JavaModelException {
		return this.checkStructure(member, Messages.CUContainsCompileErrors);
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

			OptimizeStreamRefactoringDescriptor descriptor = new OptimizeStreamRefactoringDescriptor(null, "TODO", null,
					arguments, flags);

			return new DynamicValidationRefactoringChange(descriptor, this.getProcessorName(), manager.getAllChanges());
		} finally {
			pm.done();
			this.clearCaches();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] getElements() {
		return this.getOptimizableStreams().stream().map(s -> s.getCreation()).toArray();
	}

	public Collection<Entrypoint> getEntryPoints(IJavaProject javaProject) {
		return this.projectToEntryPoints == null ? Collections.emptySet() : this.projectToEntryPoints.get(javaProject);
	}

	@Override
	public String getIdentifier() {
		return OptimizeStreamRefactoringDescriptor.REFACTORING_ID;
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
		Set<Stream> streamSet = this.getStreamSet();
		return streamSet == null ? Collections.emptySet()
				: streamSet.parallelStream().filter(s -> !s.getStatus().hasError()).collect(Collectors.toSet());
	}

	@Override
	public String getProcessorName() {
		return Messages.Name;
	}

	public Set<Stream> getStreamSet() {
		return this.streamSet;
	}

	public Set<Stream> getUnoptimizableStreams() {
		return this.getStreamSet().parallelStream().filter(s -> s.getStatus().hasError()).collect(Collectors.toSet());
	}

	public boolean getUseImplicitBenchmarkEntrypoints() {
		return this.useImplicitBenchmarkEntrypoints;
	}

	public boolean getUseImplicitEntrypoints() {
		return this.useImplicitEntrypoints;
	}

	public boolean getUseImplicitJavaFXEntrypoints() {
		return this.useImplicitJavaFXEntrypoints;
	}

	public boolean getUseImplicitTestEntrypoints() {
		return this.useImplicitTestEntrypoints;
	}

	@Override
	public boolean isApplicable() throws CoreException {
		return true;
	}

	@SuppressWarnings("unused")
	private void logInfo(String message) {
		log(IStatus.INFO, message);
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

	public void setUseImplicitBenchmarkEntrypoints(boolean useImplicitBenchmarkEntrypoints) {
		this.useImplicitBenchmarkEntrypoints = useImplicitBenchmarkEntrypoints;
	}

	public void setUseImplicitEntrypoints(boolean useImplicitEntrypoints) {
		this.useImplicitEntrypoints = useImplicitEntrypoints;
	}

	public void setUseImplicitJavaFXEntrypoints(boolean useImplicitJavaFXEntrypoints) {
		this.useImplicitJavaFXEntrypoints = useImplicitJavaFXEntrypoints;
	}

	public void setUseImplicitTestEntrypoints(boolean useImplicitTestEntrypoints) {
		this.useImplicitTestEntrypoints = useImplicitTestEntrypoints;
	}
}
