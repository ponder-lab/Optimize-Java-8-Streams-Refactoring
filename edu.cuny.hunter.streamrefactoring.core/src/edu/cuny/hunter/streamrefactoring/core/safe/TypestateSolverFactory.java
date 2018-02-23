package edu.cuny.hunter.streamrefactoring.core.safe;

import com.ibm.safe.controller.ISafeSolver;
import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.reporting.IReporter;
import com.ibm.safe.typestate.controller.TypeStateSolverCreator;
import com.ibm.safe.typestate.controller.TypeStateSolverKind;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.merge.IMergeFunctionFactory;
import com.ibm.safe.typestate.metrics.TypeStateMetrics;
import com.ibm.safe.typestate.mine.TraceReporter;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.safe.typestate.unique.UniqueSolver;
import com.ibm.wala.analysis.pointers.HeapGraph;
import com.ibm.wala.escape.ILiveObjectAnalysis;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.CancelException;

public class TypestateSolverFactory extends com.ibm.safe.typestate.core.TypestateSolverFactory {

	protected TypestateSolverFactory() {
	}

	public static TrackingUniqueSolver getSolver(CallGraph cg, PointerAnalysis<?> pointerAnalysis, HeapGraph<?> hg,
			TypeStateProperty dfa, BenignOracle ora, TypeStateOptions options, TypeStateMetrics metrics,
			IReporter reporter, TraceReporter traceReporter, SSAInvokeInstruction instruction)
			throws PropertiesException, CancelException {
		return getSolver(options.getTypeStateSolverKind(), cg, pointerAnalysis, hg, dfa, ora, options, metrics,
				reporter, traceReporter, instruction);
	}

	public static TrackingUniqueSolver getSolver(TypeStateSolverKind kind, CallGraph cg,
			PointerAnalysis<?> pointerAnalysis, HeapGraph<?> hg, TypeStateProperty dfa, BenignOracle ora,
			TypeStateOptions options, TypeStateMetrics metrics, IReporter reporter, TraceReporter traceReporter,
			SSAInvokeInstruction instruction) throws PropertiesException {
		IMergeFunctionFactory mergeFactory = makeMergeFactory(options, kind);
		ILiveObjectAnalysis live = getLiveObjectAnalysis(cg, hg, options);
		return new InstructionBasedSolver(cg, pointerAnalysis, dfa, options, live, ora, metrics, reporter,
				traceReporter, mergeFactory, instruction);
	}

	public static ISafeSolver getSolver(AnalysisOptions domoOptions, CallGraph cg,
			PointerAnalysis<?> pointerAnalysis, HeapGraph<?> hg, TypeStateProperty dfa, BenignOracle ora,
			TypeStateOptions options, TypeStateMetrics metrics, IReporter reporter, TraceReporter traceReporter)
			throws PropertiesException, CancelException {
		IMergeFunctionFactory mergeFactory = makeMergeFactory(options, TypeStateSolverKind.UNIQUE);
		ILiveObjectAnalysis live = getLiveObjectAnalysis(cg, hg, options);
		return new UniqueSolver(cg, pointerAnalysis, dfa, options, live, ora, metrics, reporter, traceReporter, mergeFactory);
	}

	protected static ILiveObjectAnalysis getLiveObjectAnalysis(CallGraph cg, HeapGraph<?> hg, TypeStateOptions options)
			throws PropertiesException {
		return options.shouldUseLiveAnalysis()
				? TypeStateSolverCreator.computeLiveObjectAnalysis(cg, hg, false) : null;
	}
}
