package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.reporting.IReporter;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.merge.IMergeFunctionFactory;
import com.ibm.safe.typestate.metrics.TypeStateMetrics;
import com.ibm.safe.typestate.mine.TraceReporter;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.safe.typestate.rules.ITypeStateDFA;
import com.ibm.safe.typestate.unique.UniqueSolver;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.escape.ILiveObjectAnalysis;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;

import edu.cuny.hunter.streamrefactoring.core.analysis.Util;
import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;
import edu.cuny.hunter.streamrefactoring.core.wala.CallStringWithReceivers;

/**
 * A solver that only tracks instances created from client (non-JDK) calls.
 * 
 * @author <a href="mailto:rk1424@hunter.cuny.edu">Raffi Khatchadourian</a>
 */
public class ClientSlicingUniqueSolver extends UniqueSolver {

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	public ClientSlicingUniqueSolver(CallGraph cg, PointerAnalysis pointerAnalysis, ITypeStateDFA dfa,
			TypeStateOptions options, ILiveObjectAnalysis live, BenignOracle ora, TypeStateMetrics metrics,
			IReporter reporter, TraceReporter traceReporter, IMergeFunctionFactory mergeFactory) {
		super(cg, pointerAnalysis, dfa, options, live, ora, metrics, reporter, traceReporter, mergeFactory);
	}

	@Override
	protected Collection<InstanceKey> computeTrackedInstances() throws PropertiesException {
		Collection<InstanceKey> instances = super.computeTrackedInstances();

		for (Iterator<InstanceKey> iterator = instances.iterator(); iterator.hasNext();) {
			InstanceKey instanceKey = iterator.next();
			CallStringWithReceivers callString = Util.getCallString(instanceKey);
			IMethod[] callingMethods = callString.getMethods();
			IMethod outerMostCallingMethod = callingMethods[1];
			MethodReference reference = outerMostCallingMethod.getReference();
			TypeReference declaringClass = reference.getDeclaringClass();
			TypeName name = declaringClass.getName();
			Atom classPackage = name.getPackage();
			boolean isFromAPI = classPackage.startsWith(Atom.findOrCreateAsciiAtom("java"));

			// if it's being called from the API.
			if (isFromAPI) {
				// remove it.
				LOGGER.info(() -> "Removing instance: " + instanceKey);
				iterator.remove();
			}
		}

		return instances;
	}

}
