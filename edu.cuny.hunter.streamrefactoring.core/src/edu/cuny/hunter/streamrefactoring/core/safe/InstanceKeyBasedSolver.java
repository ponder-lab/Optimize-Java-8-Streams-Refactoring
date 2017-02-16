package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.Collection;
import java.util.Iterator;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.reporting.IReporter;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.core.TypeStatePropertyContext;
import com.ibm.safe.typestate.merge.IMergeFunctionFactory;
import com.ibm.safe.typestate.metrics.TypeStateMetrics;
import com.ibm.safe.typestate.mine.TraceReporter;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.safe.typestate.unique.UniqueSolver;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.escape.ILiveObjectAnalysis;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.util.collections.Pair;

public class InstanceKeyBasedSolver extends UniqueSolver {

	// TODO: Don't we want the instance key here?
	private SSAInvokeInstruction instruction;

	public InstanceKeyBasedSolver(CallGraph cg, PointerAnalysis<?> pointerAnalysis, TypeStateProperty property,
			TypeStateOptions options, ILiveObjectAnalysis live, BenignOracle ora, TypeStateMetrics metrics,
			IReporter reporter, TraceReporter traceReporter, IMergeFunctionFactory mergeFactory,
			SSAInvokeInstruction instruction) {
		super(cg, pointerAnalysis, property, options, live, ora, metrics, reporter, traceReporter, mergeFactory);
		this.instruction = instruction;
	}

	@Override
	protected Collection<InstanceKey> computeTrackedInstances() throws PropertiesException {
		// TODO: Not quite sure what to do here.
		for (Iterator<?> iterator = this.getPointerAnalysis().getInstanceKeys().iterator(); iterator.hasNext();) {
			InstanceKey key = (InstanceKey) iterator.next();
			IClass concreteType = key.getConcreteType();
			if (TypeStatePropertyContext.isTrackedType(this.getCallGraph().getClassHierarchy(), this.getDFA().getTypes(), concreteType)) {
				// here, we know it's the same type.
				Iterator<Pair<CGNode, NewSiteReference>> creationSites = key.getCreationSites(this.getCallGraph());
				System.out.println(creationSites);
				while (creationSites.hasNext()) {
					Pair<CGNode,NewSiteReference> pair = creationSites.next();
					System.out.println(pair);
					CGNode fst = pair.fst;
					IR ir = fst.getIR();
					System.out.println(ir);
					SSANewInstruction ssaNewInstruction = ir.getNew(pair.snd);
					System.out.println(ssaNewInstruction);
					
					NewSiteReference snd = pair.snd;
					System.out.println(snd);
				}
			}
		}
		return this.computeTrackedInstancesByType();
	}

	protected SSAInvokeInstruction getInstruction() {
		return this.instruction;
	}
}
