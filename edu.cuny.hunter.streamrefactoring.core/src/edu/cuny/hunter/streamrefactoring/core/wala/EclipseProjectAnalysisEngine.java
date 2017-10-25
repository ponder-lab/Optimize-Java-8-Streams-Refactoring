/**
 * This class derives from https://github.com/reprogrammer/keshmesh/ and is licensed under Illinois Open Source License.
 */
package edu.cuny.hunter.streamrefactoring.core.wala;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

import com.ibm.wala.cast.java.client.JDTJavaSourceAnalysisEngine;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ide.util.EclipseProjectPath.AnalysisScopeType;
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

import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;

/**
 * Modified from EclipseAnalysisEngine.java, originally from Keshmesh. Authored
 * by Mohsen Vakilian and Stas Negara. Modified by Nicholas Chen and Raffi
 * Khatchadourian.
 * 
 */
public class EclipseProjectAnalysisEngine<I extends InstanceKey> extends JDTJavaSourceAnalysisEngine<I> {

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	/**
	 * The N value used to create the {@link nCFABuilder}.
	 */
	private static final int N = 1;

	private CallGraphBuilder<?> callGraphBuilder;

	public EclipseProjectAnalysisEngine(IJavaProject project) throws IOException, CoreException {
		super(project);
	}

	@Override
	public void buildAnalysisScope() throws IOException {
		try {
			ePath = createProjectPath(project);
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

			scope.addToScope(ClassLoaderReference.Primordial, new JarFile(
					installLocation.toPath().resolve("jre").resolve("lib").resolve("resources.jar").toFile()));
			scope.addToScope(ClassLoaderReference.Primordial,
					new JarFile(installLocation.toPath().resolve("jre").resolve("lib").resolve("rt.jar").toFile()));
			scope.addToScope(ClassLoaderReference.Primordial,
					new JarFile(installLocation.toPath().resolve("jre").resolve("lib").resolve("jsse.jar").toFile()));
			scope.addToScope(ClassLoaderReference.Primordial,
					new JarFile(installLocation.toPath().resolve("jre").resolve("lib").resolve("jce.jar").toFile()));
			scope.addToScope(ClassLoaderReference.Primordial, new JarFile(
					installLocation.toPath().resolve("jre").resolve("lib").resolve("charsets.jar").toFile()));
		}

		if (getExclusionsFile() != null) {
			InputStream stream = this.getClass().getResourceAsStream("/EclipseDefaultExclusions.txt");
			scope.setExclusions(new FileOfClasses(stream));
		}
	}

	@Override
	protected EclipseProjectPath<?, IJavaProject> createProjectPath(IJavaProject project)
			throws IOException, CoreException {
		project.open(new NullProgressMonitor());
		return TestableJavaEclipseProjectPath.create(project, AnalysisScopeType.NO_SOURCE);
	}

	@Override
	public CallGraph getCallGraph() {
		return super.getCallGraph();
	}

	@Override
	public IClassHierarchy buildClassHierarchy() {
		IClassHierarchy classHierarchy = super.buildClassHierarchy();
		this.setClassHierarchy(classHierarchy);
		return classHierarchy;
	}

	public CallGraph buildSafeCallGraph(AnalysisOptions options)
			throws IllegalArgumentException, CallGraphBuilderCancelException, CancelException {
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

	public CallGraphBuilder<?> getCallGraphBuilder() {
		return callGraphBuilder;
	}

	@Override
	protected CallGraphBuilder<?> getCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options,
			IAnalysisCacheView cache) {
		return Util.makeNCFABuilder(N, options, (AnalysisCache) cache, cha, scope);
	}

	public void clearCallGraphBuilder() {
		this.callGraphBuilder = null;
	}
}
