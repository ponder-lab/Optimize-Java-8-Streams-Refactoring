package edu.cuny.hunter.streamrefactoring.core.wala;

import com.ibm.wala.analysis.reflection.ReflectionContextInterpreter;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.DefaultContextSelector;
import com.ibm.wala.ipa.callgraph.impl.DelegatingContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.ClassBasedInstanceKeys;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.DefaultPointerKeyFactory;
import com.ibm.wala.ipa.callgraph.propagation.cfa.DefaultSSAInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.cfa.DelegatingSSAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class nCFABuilderWithActualParametersInContext extends SSAPropagationCallGraphBuilder {

	public nCFABuilderWithActualParametersInContext(int n, IClassHierarchy cha, AnalysisOptions options,
			AnalysisCache cache, ContextSelector appContextSelector, SSAContextInterpreter appContextInterpreter) {
		super(cha, options, cache, new DefaultPointerKeyFactory());
		if (options == null) {
			throw new IllegalArgumentException("options is null");
		}

		setInstanceKeys(new ClassBasedInstanceKeys(options, cha));

		ContextSelector def = new DefaultContextSelector(options, cha);
		ContextSelector contextSelector = appContextSelector == null ? def
				: new DelegatingContextSelector(appContextSelector, def);
		contextSelector = new nCFAContextWithReceiversSelector(n, contextSelector);
		setContextSelector(contextSelector);

		SSAContextInterpreter defI = new DefaultSSAInterpreter(options, cache);
		defI = new DelegatingSSAContextInterpreter(
				ReflectionContextInterpreter.createReflectionContextInterpreter(cha, options, getAnalysisCache()),
				defI);
		SSAContextInterpreter contextInterpreter = appContextInterpreter == null ? defI
				: new DelegatingSSAContextInterpreter(appContextInterpreter, defI);
		setContextInterpreter(contextInterpreter);
	}

}
