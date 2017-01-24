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
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.util.config.FileOfClasses;

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
}
