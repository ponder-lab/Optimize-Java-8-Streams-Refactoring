/**
 * This class derives from https://github.com/reprogrammer/keshmesh/ and is licensed under Illinois Open Source License.
 */
package edu.cuny.hunter.streamrefactoring.core.wala;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;

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
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.FileOfClasses;

/**
 * Modified from EclipseAnalysisEngine.java, originally from Keshmesh. Authored
 * by Mohsen Vakilian and Stas Negara. Modified by Nicholas Chen and Raffi
 * Khatchadourian.
 * 
 */
public class EclipseProjectAnalysisEngine<I extends InstanceKey> extends JDTJavaSourceAnalysisEngine<I> {

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
		super.scope = ePath.toAnalysisScope(makeAnalysisScope());
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
		callGraphBuilder = buildCallGraph(this.getClassHierarchy(), options, true, null);
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
}
