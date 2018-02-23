package edu.cuny.hunter.streamrefactoring.core.wala;

import static com.ibm.wala.ipa.callgraph.impl.Util.addDefaultBypassLogic;
import static com.ibm.wala.ipa.callgraph.impl.Util.addDefaultSelectors;
import static com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys.ALLOCATIONS;
import static com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys.SMUSH_MANY;
import static com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys.SMUSH_PRIMITIVE_HOLDERS;
import static com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys.SMUSH_STRINGS;
import static com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys.SMUSH_THROWABLES;
import static com.ibm.wala.types.ClassLoaderReference.Application;
import static com.ibm.wala.types.ClassLoaderReference.Extension;
import static com.ibm.wala.types.ClassLoaderReference.Primordial;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.BaseStream;

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

	private static final String OS_NAME = "os.name";
	private static final String WINDOWS = "Windows";

	/**
	 * Enhance an {@link AnalysisScope} to include in a particular loader, elements
	 * from a set of Eclipse projects
	 *
	 * @param loader
	 *            the class loader in which new {@link Module}s will live
	 * @param projectPaths
	 *            Eclipse project paths to add to the analysis scope
	 * @param scope
	 *            the {@link AnalysisScope} under construction. This will be
	 *            mutated.
	 * @param seen
	 *            set of {@link Module}s which have already been seen, and should
	 *            not be added to the analysis scope
	 */
	private static void buildScope(ClassLoaderReference loader, Collection<EclipseProjectPath> projectPaths,
			AnalysisScope scope, Collection<Module> seen) throws IOException {
		for (EclipseProjectPath path : projectPaths) {
			AnalysisScope pScope = path.toAnalysisScope((File) null);
			for (Module m : pScope.getModules(loader))
				if (!seen.contains(m)) {
					seen.add(m);
					scope.addToScope(loader, m);
				}
		}
	}

	public static String getOSName() {
		return System.getProperty(OS_NAME);
	}

	public static boolean isWindows() {
		return getOSName().startsWith(WINDOWS);
	}

	/**
	 * make a {@link CallGraphBuilder} that uses call-string context sensitivity,
	 * with call-string length limited to n, and a context-sensitive
	 * allocation-site-based heap abstraction.
	 *
	 * @param nToUseForStreams
	 *            The N to use specifically for instances of {@link BaseStream}.
	 */
	public static SSAPropagationCallGraphBuilder makeNCFABuilder(int n, AnalysisOptions options, AnalysisCache cache,
			IClassHierarchy cha, AnalysisScope scope, int nToUseForStreams) {
		if (options == null)
			throw new IllegalArgumentException("options is null");
		addDefaultSelectors(options, cha);
		addDefaultBypassLogic(options, scope, Util.class.getClassLoader(), cha);
		ContextSelector appSelector = null;
		SSAContextInterpreter appInterpreter = null;
		SSAPropagationCallGraphBuilder result = new nCFABuilderWithActualParametersInContext(n, cha, options, cache,
				appSelector, appInterpreter, nToUseForStreams);
		// nCFABuilder uses type-based heap abstraction by default, but we want
		// allocation sites
		result.setInstanceKeys(new ZeroXInstanceKeys(options, cha, result.getContextInterpreter(),
				ALLOCATIONS | SMUSH_MANY | SMUSH_PRIMITIVE_HOLDERS | SMUSH_STRINGS | SMUSH_THROWABLES));
		return result;
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
		buildScope(Application, projectPaths, scope, seen);
		buildScope(Extension, projectPaths, scope, seen);
		buildScope(Primordial, projectPaths, scope, seen);
		return scope;
	}

	private Util() {
	}
}
