package edu.cuny.hunter.streamrefactoring.core.safe;

import static com.ibm.safe.typestate.core.TypestateSolverFactory.makeMergeFactory;

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
import com.ibm.wala.analysis.pointers.HeapGraph;
import com.ibm.wala.escape.ILiveObjectAnalysis;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.CancelException;

public final class TypestateSolverFactory {

	private TypestateSolverFactory() {
	}

	public static ISafeSolver getSolver(CallGraph cg, PointerAnalysis<?> pointerAnalysis, HeapGraph<?> hg,
			TypeStateProperty dfa, BenignOracle ora, TypeStateOptions options, TypeStateMetrics metrics,
			IReporter reporter, TraceReporter traceReporter, SSAInvokeInstruction instruction)
			throws PropertiesException, CancelException {
		return getSolver(options.getTypeStateSolverKind(), cg, pointerAnalysis, hg, dfa, ora, options, metrics,
				reporter, traceReporter, instruction);
	}

	public static ISafeSolver getSolver(TypeStateSolverKind kind, CallGraph cg, PointerAnalysis<?> pointerAnalysis,
			HeapGraph<?> hg, TypeStateProperty dfa, BenignOracle ora, TypeStateOptions options, TypeStateMetrics metrics,
			IReporter reporter, TraceReporter traceReporter, SSAInvokeInstruction instruction)
			throws PropertiesException {
		IMergeFunctionFactory mergeFactory = makeMergeFactory(options, kind);
		ILiveObjectAnalysis live = options.shouldUseLiveAnalysis()
				? TypeStateSolverCreator.computeLiveObjectAnalysis(cg, hg, false) : null;
		return new InstructionBasedSolver(cg, pointerAnalysis, dfa, options, live, ora, metrics, reporter,
				traceReporter, mergeFactory, instruction);
	}
}
