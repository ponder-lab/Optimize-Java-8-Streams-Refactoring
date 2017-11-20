package edu.cuny.hunter.streamrefactoring.core.wala;

import static com.ibm.wala.ipa.callgraph.impl.Util.addDefaultBypassLogic;
import static com.ibm.wala.ipa.callgraph.impl.Util.addDefaultSelectors;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import com.ibm.wala.classLoader.Module;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.collections.HashSetFactory;

public final class Util {

	private Util() {
	}
	
	public static String getOsName() {	
		return System.getProperty("os.name");
	}

	public static boolean isWindows() {
		return getOsName().startsWith("Windows");
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
	
	/**
	   * make a {@link CallGraphBuilder} that uses call-string context sensitivity,
	   * with call-string length limited to n, and a context-sensitive
	   * allocation-site-based heap abstraction.
	   */
	  public static SSAPropagationCallGraphBuilder makeNCFABuilder(int n, AnalysisOptions options, AnalysisCache cache,
	      IClassHierarchy cha, AnalysisScope scope) {
	    if (options == null) {
	      throw new IllegalArgumentException("options is null");
	    }
	    addDefaultSelectors(options, cha);
	    addDefaultBypassLogic(options, scope, Util.class.getClassLoader(), cha);
	    ContextSelector appSelector = null;
	    SSAContextInterpreter appInterpreter = null;
	    SSAPropagationCallGraphBuilder result = new nCFABuilderWithActualParametersInContext(n, cha, options, cache, appSelector, appInterpreter);
	    // nCFABuilder uses type-based heap abstraction by default, but we want allocation sites
	    result.setInstanceKeys(new ZeroXInstanceKeys(options, cha, result.getContextInterpreter(), ZeroXInstanceKeys.ALLOCATIONS
						| ZeroXInstanceKeys.SMUSH_MANY | ZeroXInstanceKeys.SMUSH_PRIMITIVE_HOLDERS | ZeroXInstanceKeys.SMUSH_STRINGS
	        | ZeroXInstanceKeys.SMUSH_THROWABLES));
	    return result;
	  }
}
