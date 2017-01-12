package edu.cuny.hunter.streamrefactoring.core.wala;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import com.ibm.wala.classLoader.Module;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.collections.HashSetFactory;

public final class WalaUtil {

	private WalaUtil() {
	}

	/**
	 * Enhance an {@link AnalysisScope} to include in a particular loader,
	 * elements from a set of Eclipse projects
	 * 
	 * @param loader
	 *            the class loader in which new {@link Module}s will live
	 * @param projectPaths
	 *            Eclipse project paths to add to the analysis scope
	 * @param scope
	 *            the {@link AnalysisScope} under construction. This will be
	 *            mutated.
	 * @param seen
	 *            set of {@link Module}s which have already been seen, and
	 *            should not be added to the analysis scope
	 */
	private static void buildScope(ClassLoaderReference loader, Collection<EclipseProjectPath> projectPaths,
			AnalysisScope scope, Collection<Module> seen) throws IOException {
		for (EclipseProjectPath path : projectPaths) {
			AnalysisScope pScope = path.toAnalysisScope((File) null);
			for (Module m : pScope.getModules(loader)) {
				if (!seen.contains(m)) {
					seen.add(m);
					scope.addToScope(loader, m);
				}
			}
		}
	}

	/**
	 * create an analysis scope as the union of a bunch of EclipseProjectPath
	 */
	public static AnalysisScope mergeProjectPaths(Collection<EclipseProjectPath> projectPaths) throws IOException {
		AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();

		Collection<Module> seen = HashSetFactory.make();
		// to avoid duplicates, we first add all application modules, then
		// extension
		// modules, then primordial
		buildScope(ClassLoaderReference.Application, projectPaths, scope, seen);
		buildScope(ClassLoaderReference.Extension, projectPaths, scope, seen);
		buildScope(ClassLoaderReference.Primordial, projectPaths, scope, seen);
		return scope;
	}
}
