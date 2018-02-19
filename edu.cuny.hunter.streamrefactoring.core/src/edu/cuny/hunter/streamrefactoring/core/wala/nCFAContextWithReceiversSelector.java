package edu.cuny.hunter.streamrefactoring.core.wala;

import static edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames.LOGGER_NAME;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.BaseStream;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallString;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContext;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFAContextSelector;
import com.ibm.wala.types.TypeReference;

import edu.cuny.hunter.streamrefactoring.core.analysis.Util;

public class nCFAContextWithReceiversSelector extends nCFAContextSelector {

	private static final Logger LOGGER = Logger.getLogger(LOGGER_NAME);

	protected class CallStringTriple {

		CGNode node;

		CallSiteReference site;
		IMethod target;

		public CallStringTriple(CGNode node, CallSiteReference site, IMethod target) {
			this.node = node;
			this.site = site;
			this.target = target;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof CallStringTriple) {
				CallStringTriple rhs = (CallStringTriple) obj;
				return this.node.equals(rhs.node) && this.site.equals(rhs.site) && this.target.equals(rhs.target);
			} else
				return false;
		}

		@Override
		public int hashCode() {
			StringBuilder builder = new StringBuilder();

			builder.append(this.node.hashCode());
			builder.append(this.site.hashCode());
			builder.append(this.target.hashCode());

			return builder.toString().hashCode();
		}
	}

	/**
	 * The default N to use if the instance implements BaseStream.
	 */
	protected static final int CONTEXT_LENGTH_FOR_STREAMS_DEFAULT = 2;

	/**
	 * The N to use if the instance implements {@link BaseStream}.
	 */
	private int contextLengthForStreams = CONTEXT_LENGTH_FOR_STREAMS_DEFAULT;

	protected Map<CallStringTriple, CallStringWithReceivers> callStringWithReceiversMap = new HashMap<>();

	/**
	 * Create a new {@link nCFAContextWithReceiversSelector}.
	 * 
	 * @param n
	 *            The N to use generally.
	 * @param base
	 *            The base {@link ContextSelector}.
	 */
	public nCFAContextWithReceiversSelector(int n, ContextSelector base) {
		super(n, base);
	}

	/**
	 * Create a new {@link nCFAContextWithReceiversSelector}.
	 * 
	 * @param n
	 *            The N to use generally.
	 * @param base
	 *            The base {@link ContextSelector}.
	 * @param nToUseForStreams
	 *            The particular N to use if the instance is ok {@link BaseStream}.
	 */
	public nCFAContextWithReceiversSelector(int n, ContextSelector base, int nToUseForStreams) {
		super(n, base);
		LOGGER.info(() -> "Using N = " + nToUseForStreams);
		this.contextLengthForStreams = nToUseForStreams;
	}

	@Override
	public Context getCalleeTarget(CGNode caller, CallSiteReference site, IMethod callee,
			InstanceKey[] actualParameters) {
		Context baseContext = this.base.getCalleeTarget(caller, site, callee, actualParameters);
		CallStringWithReceivers cs = this.getCallString(caller, site, callee, actualParameters);
		if (cs == null)
			return baseContext;
		else if (baseContext == Everywhere.EVERYWHERE)
			return new CallStringContext(cs);
		else
			return new CallStringContextPair(cs, baseContext);
	}

	protected CallStringWithReceivers getCallString(CGNode caller, CallSiteReference site, IMethod target,
			InstanceKey[] actualParameters) {
		CallStringTriple triple = new CallStringTriple(caller, site, target);

		if (this.getCallStringWithReceiversMap().containsKey(triple)) {
			// found.
			CallStringWithReceivers ret = this.getCallStringWithReceiversMap().get(triple);

			// add the receiver.
			if (actualParameters != null && actualParameters.length > 0)
				ret.addPossibleReceiver(actualParameters[0]);

			return ret;
		} else {
			// not found. Compute it.
			CallStringWithReceivers ret = null;
			int length = this.getLength(caller, site, target);

			if (length > 0) {
				CallString callString = (CallString) caller.getContext().get(CALL_STRING);

				if (callString != null)
					ret = new CallStringWithReceivers(site, caller.getMethod(), length, callString);
				else
					ret = new CallStringWithReceivers(site, caller.getMethod());
			} else
				ret = null;

			// if we have a receiver.
			if (ret != null && actualParameters != null && actualParameters.length > 0)
				// add it.
				ret.addPossibleReceiver(actualParameters[0]);

			this.getCallStringWithReceiversMap().put(triple, ret);
			return ret;
		}
	}

	protected Map<CallStringTriple, CallStringWithReceivers> getCallStringWithReceiversMap() {
		return this.callStringWithReceiversMap;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return CONTEXT_LENGTH_FOR_STREAMS if the target's return type implements
	 *         {@link BaseStream}, otherwise, return the original value.
	 */
	@Override
	protected int getLength(CGNode caller, CallSiteReference site, IMethod target) {
		TypeReference typeToCheck = Util.getEvaluationType(target);
		boolean implementsBaseStream = Util.implementsBaseStream(typeToCheck, target.getClassHierarchy());

		if (implementsBaseStream) {
			int lengthForStreams = this.getContextLengthForStreams();
			LOGGER.fine(() -> "Using N = " + lengthForStreams);
			return lengthForStreams;
		} else
			return super.getLength(caller, site, target);
	}

	public int getContextLengthForStreams() {
		return contextLengthForStreams;
	}

	protected void setContextLengthForStreams(int contextLengthForStreams) {
		this.contextLengthForStreams = contextLengthForStreams;
	}
}