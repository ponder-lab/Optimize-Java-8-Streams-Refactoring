/**
 * This class derives from https://github.com/reprogrammer/keshmesh/ and is licensed under Illinois Open Source License.
 */
package edu.cuny.hunter.streamrefactoring.core.wala;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaProject;
import org.osgi.framework.Bundle;

import com.ibm.wala.cast.java.client.JDTJavaSourceAnalysisEngine;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.ide.util.EclipseFileProvider;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.BypassMethodTargetSelector;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.strings.Atom;

//import edu.illinois.jflow.wala.core.Activator;
//import edu.illinois.jflow.wala.pointeranalysis.AnalysisUtils;

/**
 * Modified from EclipseAnalysisEngine.java, originally from Keshmesh. Authored
 * by Mohsen Vakilian and Stas Negara. Modified by Nicholas Chen and Raffi
 * Khatchadourian.
 * 
 */
public class EclipseProjectAnalysisEngine<I extends InstanceKey> extends JDTJavaSourceAnalysisEngine<I> {

	public EclipseProjectAnalysisEngine(IJavaProject project) throws IOException, CoreException {
		super(project);
	}

	private String retrieveExclusionFile() throws IOException {
		URL url = new URL("platform:/plugin/edu.cuny.hunter.streamrefactoring.core/EclipseDefaultExclusions.txt");
		File file = null;
		try {
			file = new File(FileLocator.resolve(url).toURI());
		} catch (URISyntaxException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return file.getAbsolutePath();
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
			File file = new File(retrieveExclusionFile());
			FileInputStream stream = new FileInputStream(file);
			scope.setExclusions(new FileOfClasses(stream));
		}
	}

	@Override
	protected Iterable<Entrypoint> makeDefaultEntrypoints(AnalysisScope scope, IClassHierarchy cha) {
		return Util.makeMainEntrypoints(JavaSourceAnalysisScope.SOURCE, cha);
	}

	@Override
	protected CallGraphBuilder getCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options, AnalysisCache cache) {
		// return (CallGraphBuilder)JFlowAnalysisUtil.getCallGraphBuilder(scope,
		// cha, options, cache);
		return null;
	}

}

class JFlowBypassMethodTargetSelector extends BypassMethodTargetSelector {

	public JFlowBypassMethodTargetSelector(MethodTargetSelector parent,
			Map<MethodReference, MethodSummary> methodSummaries, Set<Atom> ignoredPackages, IClassHierarchy cha) {
		super(parent, methodSummaries, ignoredPackages, cha);
	}

	@Override
	protected boolean canIgnore(MemberReference m) {
		if (AnalysisUtils.isLibraryClass(m.getDeclaringClass())) {
			return true;
		} else {
			return super.canIgnore(m);
		}
	}
}