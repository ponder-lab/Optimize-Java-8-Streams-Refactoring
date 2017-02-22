package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.safe.controller.ISafeSolver;
import com.ibm.safe.dfa.DFASpec;
import com.ibm.safe.dfa.DFAState;
import com.ibm.safe.dfa.DFATransition;
import com.ibm.safe.dfa.IDFAState;
import com.ibm.safe.dfa.IDFATransition;
import com.ibm.safe.dfa.events.IDispatchEvent;
import com.ibm.safe.dfa.events.IDispatchEventImpl;
import com.ibm.safe.internal.exceptions.MaxFindingsException;
import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.safe.internal.exceptions.SetUpException;
import com.ibm.safe.internal.exceptions.SolverTimeoutException;
import com.ibm.safe.options.WholeProgramProperties;
import com.ibm.safe.properties.PropertiesManager;
import com.ibm.safe.rules.TypestateRule;
import com.ibm.safe.typestate.core.BenignOracle;
import com.ibm.safe.typestate.core.TypeStateProperty;
import com.ibm.safe.typestate.options.TypeStateOptions;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import edu.cuny.hunter.streamrefactoring.core.safe.ModifiedBenignOracle;
import edu.cuny.hunter.streamrefactoring.core.safe.TypestateSolverFactory;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

class StateMachine {

	/**
	 * The stream that this state machine represents.
	 */
	private final Stream stream;

	/**
	 * Constructs a new {@link StateMachine} given a {@link Stream} to
	 * represent.
	 * 
	 * @param stream
	 *            The representing stream.
	 */
	StateMachine(Stream stream) {
		this.stream = stream;
	}

	public void start() throws IOException, CoreException, CallGraphBuilderCancelException, CancelException,
			InvalidClassFileException, PropertiesException {
		EclipseProjectAnalysisEngine<InstanceKey> engine = this.getStream().getAnalysisEngine();

		// FIXME: Do we want a different entry point?
		DefaultEntrypoint entryPoint = new DefaultEntrypoint(this.getStream().getEnclosingMethodReference(),
				this.getStream().getClassHierarchy());
		Set<Entrypoint> entryPoints = Collections.singleton(entryPoint);

		// FIXME: Do we need to build a new call graph for each entry point?
		// Doesn't make sense. Maybe we need to collect all enclosing
		// methods and use those as entry points.
		engine.buildSafeCallGraph(entryPoints);
		// TODO: Can I slice the graph so that only nodes relevant to the
		// instance in question are present?

		BenignOracle ora = new ModifiedBenignOracle(engine.getCallGraph(), engine.getPointerAnalysis());

		PropertiesManager manager = PropertiesManager.initFromMap(Collections.emptyMap());
		PropertiesManager.registerProperties(
				new PropertiesManager.IPropertyDescriptor[] { WholeProgramProperties.Props.LIVE_ANALYSIS });
		TypeStateOptions typeStateOptions = new TypeStateOptions(manager);
		typeStateOptions.setBooleanValue(WholeProgramProperties.Props.LIVE_ANALYSIS.getName(), false);

		TypeReference typeReference = this.getStream().getTypeReference();
		IClass streamClass = engine.getClassHierarchy().lookupClass(typeReference);

		TypestateRule rule = new TypestateRule();
		rule.addType(streamClass.getName().toString());
		rule.setName("execution mode");

		addAutomaton(rule);

		TypeStateProperty dfa = new TypeStateProperty(rule, engine.getClassHierarchy());

		ISafeSolver solver = TypestateSolverFactory.getSolver(engine.getCallGraph(), engine.getPointerAnalysis(),
				engine.getHeapGraph(), dfa, ora, typeStateOptions, null, null, null,
				this.getStream().getInstructionForCreation().get());
		try {
			solver.perform(new NullProgressMonitor());
		} catch (SolverTimeoutException | MaxFindingsException | SetUpException | WalaException e) {
			Logger.getGlobal().log(Level.SEVERE, "Exception caught during typestate analysis.", e);
			throw new RuntimeException(e);
		}
	}

	protected Stream getStream() {
		return stream;
	}

	private static void addAutomaton(TypestateRule rule) {
		DFASpec automaton = new DFASpec();

		IDFAState bottom = addState(automaton, "bottom", true);
		IDFAState sequential = addState(automaton, "sequential");
		IDFAState parallel = addState(automaton, "parallel");
		IDFAState closed = addState(automaton, "closed");
		IDFAState counted = addState(automaton, "counted");

		IDispatchEvent close = addEvent(automaton, "close", ".*close\\(.*\\).*");

		addTransition(automaton, bottom, closed, close);

		rule.setTypeStateAutomaton(automaton);
	}

	private static IDFATransition addTransition(DFASpec automaton, IDFAState source, IDFAState destination,
			IDispatchEvent event) {
		IDFATransition transition = new DFATransition();
		transition.setSource(source);
		transition.setEvent(event);
		transition.setDestination(destination);
		automaton.addTransition(transition);
		return transition;
	}

	private static IDispatchEvent addEvent(DFASpec automaton, String eventName, String eventPattern) {
		IDispatchEventImpl close = new IDispatchEventImpl();
		close.setName(eventName);
		close.setPattern(eventPattern);
		automaton.addEvent(close);
		return close;
	}

	private static IDFAState addState(DFASpec automaton, String stateName) {
		return addState(automaton, stateName, false);
	}

	private static IDFAState addState(DFASpec automaton, String stateName, boolean initialState) {
		IDFAState state = new DFAState();

		state.setName(stateName);
		automaton.addState(state);

		if (initialState)
			automaton.setInitialState(state);

		return state;
	}
}