package edu.cuny.hunter.streamrefactoring.core.wala;

import com.ibm.wala.cast.java.client.impl.ZeroCFABuilderFactory;
import com.ibm.wala.cast.java.ipa.callgraph.AstJavaZeroXCFABuilder;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class ZeroOneCFABuilderFactory extends ZeroCFABuilderFactory {

	@Override
	public CallGraphBuilder<?> make(AnalysisOptions options, AnalysisCache cache, IClassHierarchy cha,
			AnalysisScope scope, boolean keepPointsTo) {
		Util.addDefaultSelectors(options, cha);
		Util.addDefaultBypassLogic(options, scope, Util.class.getClassLoader(), cha);
		return (CallGraphBuilder<?>) new AstJavaZeroXCFABuilder(cha, options, cache, null, null,
				ZeroXInstanceKeys.ALLOCATIONS);
	}
}
