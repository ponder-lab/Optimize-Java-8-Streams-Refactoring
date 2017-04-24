package edu.cuny.hunter.streamrefactoring.core.analysis;

import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;

public class Analysis {
	protected static AnalysisOptions options = new AnalysisOptions();
	protected static AnalysisCache cache = new AnalysisCacheImpl();
	
	static {
		// TODO: this.options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
	}

	protected static AnalysisOptions getOptions() {
		return options;
	}

	protected static AnalysisCache getCache() {
		return cache;
	}	
}
