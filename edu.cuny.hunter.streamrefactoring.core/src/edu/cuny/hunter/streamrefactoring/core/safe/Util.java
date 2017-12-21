package edu.cuny.hunter.streamrefactoring.core.safe;

import java.util.Iterator;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallString;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContextSelector;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.strings.Atom;

public class Util {

	private Util() {
	}

	/**
	 * True iff the given {@link InstanceKey} corresponds with the given
	 * {@link SSAInvokeInstruction} in the given {@link CallGraph}. In other
	 * words, the result is true iff the instruction is used to create the
	 * instance.
	 * 
	 * @param instanceKey
	 *            An instance in question.
	 * @param instruction
	 *            An instruction in question. Should be corresponding to a ctor
	 *            call.
	 * @param callGraph
	 *            The corresponding call graph.
	 * @return True iff the given instruction was used to instantiate the given
	 *         instance key according to the given call graph.
	 */
	public static boolean instanceKeyCorrespondsWithInstantiationInstruction(InstanceKey instanceKey,
			SSAInvokeInstruction instruction, CallGraph callGraph) {
		// Creation sites for the instance with the given key in the given call
		// graph.
		Iterator<Pair<CGNode, NewSiteReference>> creationSites = instanceKey.getCreationSites(callGraph);

		// for each creation site.
		while (creationSites.hasNext()) {
			Pair<CGNode, NewSiteReference> pair = creationSites.next();

			// get the call string of the node in the call graph.
			ContextItem contextItem = pair.fst.getContext().get(CallStringContextSelector.CALL_STRING);
			CallString callString = (CallString) contextItem;

			// get the call site references corresponding to the call string.
			CallSiteReference[] callSiteRefs = callString.getCallSiteRefs();

			// for each call site reference.
			for (CallSiteReference callSiteReference : callSiteRefs) {
				CallSiteReference instructionCallSite = instruction.getCallSite();

				// if the call site reference equals the call site corresponding
				// to the creation instruction.
				if (callSiteReference.equals(instructionCallSite))
					return true;
				// workaround #80.
				else if (callSiteReference.getProgramCounter() == instructionCallSite.getProgramCounter()) {
					// compare declared targets.
					MethodReference callSiteDeclaredTarget = callSiteReference.getDeclaredTarget();
					MethodReference instructionCallDeclaredTarget = instructionCallSite.getDeclaredTarget();

					if (callSiteDeclaredTarget.getDeclaringClass().getName()
							.equals(instructionCallDeclaredTarget.getDeclaringClass().getName())
							&& callSiteDeclaredTarget.getDeclaringClass().getName()
									.equals(TypeName.string2TypeName("Ljava/util/Arrays"))
							&& callSiteDeclaredTarget.getName().equals(instructionCallDeclaredTarget.getName())
							&& callSiteDeclaredTarget.getName().equals(Atom.findOrCreateAsciiAtom("stream")))
						return true;
				}
			}
		}
		return false;
	}

}
