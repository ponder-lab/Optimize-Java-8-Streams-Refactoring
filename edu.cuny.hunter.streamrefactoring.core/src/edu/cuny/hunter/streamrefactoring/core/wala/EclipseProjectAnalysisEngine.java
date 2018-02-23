/**
 * This class derives from https://github.com/reprogrammer/keshmesh/ and is licensed under Illinois Open Source License.
 */
package edu.cuny.hunter.streamrefactoring.core.wala;

import static com.ibm.wala.ide.util.EclipseProjectPath.AnalysisScopeType.NO_SOURCE;
import static com.ibm.wala.types.ClassLoaderReference.Primordial;
import static edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames.LOGGER_NAME;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.BaseStream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

import com.ibm.wala.cast.java.client.JDTJavaSourceAnalysisEngine;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFABuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.FileOfClasses;

/**
 * Modified from EclipseAnalysisEngine.java, originally from Keshmesh. Authored
 * by Mohsen Vakilian and Stas Negara. Modified by Nicholas Chen and Raffi
 * Khatchadourian.
 *
 */
public class EclipseProjectAnalysisEngine<I extends InstanceKey> extends JDTJavaSourceAnalysisEngine<I> {

	private static final Logger LOGGER = Logger.getLogger(LOGGER_NAME);

	/**
	 * The N value used to create the {@link nCFABuilder}.
	 */
	private static final int N = 1;

	/**
	 * The default N value used for instances of {@link BaseStream} to create the
	 * {@link nCFABuilder}.
	 */
	private static final int N_FOR_STREAMS_DEFAULT = 2;

	private CallGraphBuilder<?> callGraphBuilder;

	/**
	 * The N to use for instances of {@link BaseStream}.
	 */
	private int nToUseForStreams = N_FOR_STREAMS_DEFAULT;

	/**
	 * The project used to create this engine.
	 */
	private IJavaProject project;

	public EclipseProjectAnalysisEngine(IJavaProject project) throws IOException, CoreException {
		super(project);
		this.project = project;
	}

	public EclipseProjectAnalysisEngine(IJavaProject project, int nForStreams) throws IOException, CoreException {
		this(project);
		this.nToUseForStreams = nForStreams;
	}

	void addToScopeNotWindows(String fileName, Path installPath) throws IOException {
		scope.addToScope(Primordial, new JarFile(installPath.resolve("jre").resolve("lib").resolve(fileName).toFile()));
	}

	void addToScopeWindows(String fileName, Path installPath) throws IOException {
		scope.addToScope(Primordial, new JarFile(installPath.resolve("lib").resolve(fileName).toFile()));
	}

	@Override
	public void buildAnalysisScope() throws IOException {
		try {
			ePath = createProjectPath(getProject());
		} catch (CoreException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		scope = ePath.toAnalysisScope(makeAnalysisScope());

		// if no primordial classes are in scope.
		if (scope.getModules(ClassLoaderReference.Primordial).isEmpty()) {
			// Add "real" libraries per
			// https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/83.

			IVMInstall defaultVMInstall = JavaRuntime.getDefaultVMInstall();
			File installLocation = defaultVMInstall.getInstallLocation();
			Path installPath = installLocation.toPath();

			if (Util.isWindows()) {
				addToScopeWindows("resources.jar", installPath);
				addToScopeWindows("rt.jar", installPath);
				addToScopeWindows("jsse.jar", installPath);
				addToScopeWindows("jce.jar", installPath);
				addToScopeWindows("charsets.jar", installPath);
			} else {
				addToScopeNotWindows("resources.jar", installPath);
				addToScopeNotWindows("rt.jar", installPath);
				addToScopeNotWindows("jsse.jar", installPath);
				addToScopeNotWindows("jce.jar", installPath);
				addToScopeNotWindows("charsets.jar", installPath);
			}

		}

		if (getExclusionsFile() != null) {
			InputStream stream = this.getClass().getResourceAsStream("/EclipseDefaultExclusions.txt");
			scope.setExclusions(new FileOfClasses(stream));
		}
	}

	@Override
	public IClassHierarchy buildClassHierarchy() {
		IClassHierarchy classHierarchy = super.buildClassHierarchy();
		this.setClassHierarchy(classHierarchy);
		return classHierarchy;
	}

	public CallGraph buildSafeCallGraph(AnalysisOptions options)
			throws CallGraphBuilderCancelException, CancelException {
		LOGGER.entering(this.getClass().getName(), "buildSafeCallGraph", this.callGraphBuilder);

		if (callGraphBuilder == null) {
			LOGGER.info("Creating new call graph builder.");
			callGraphBuilder = buildCallGraph(this.getClassHierarchy(), options, true, null);
		} else
			LOGGER.info("Reusing call graph builder.");

		LOGGER.exiting(this.getClass().getName(), "buildSafeCallGraph", this.callGraphBuilder);
		return callGraphBuilder.makeCallGraph(options, null);
	}

	public CallGraph buildSafeCallGraph(Iterable<Entrypoint> entryPoints)
			throws IllegalArgumentException, CallGraphBuilderCancelException, CancelException {
		return this.buildSafeCallGraph(getDefaultOptions(entryPoints));
	}

	public void clearCallGraphBuilder() {
		this.callGraphBuilder = null;
	}

	@Override
	protected EclipseProjectPath<?, IJavaProject> createProjectPath(IJavaProject project)
			throws IOException, CoreException {
		project.open(new NullProgressMonitor());
		return TestableJavaEclipseProjectPath.create(project, NO_SOURCE);
	}

	@Override
	public CallGraph getCallGraph() {
		return super.getCallGraph();
	}

	public CallGraphBuilder<?> getCallGraphBuilder() {
		return callGraphBuilder;
	}

	@Override
	protected CallGraphBuilder<?> getCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options,
			IAnalysisCacheView cache) {
		LOGGER.info(() -> "Using N = " + this.getNToUseForStreams());
		return Util.makeNCFABuilder(N, options, (AnalysisCache) cache, cha, scope, this.getNToUseForStreams());
	}

	public int getNToUseForStreams() {
		return nToUseForStreams;
	}

	/**
	 * Get the project used to create this engine.
	 *
	 * @return The project used to create this engine.
	 */
	public IJavaProject getProject() {
		return project;
	}

	protected void setNToUseForStreams(int nToUseForStreams) {
		this.nToUseForStreams = nToUseForStreams;
	}
}
