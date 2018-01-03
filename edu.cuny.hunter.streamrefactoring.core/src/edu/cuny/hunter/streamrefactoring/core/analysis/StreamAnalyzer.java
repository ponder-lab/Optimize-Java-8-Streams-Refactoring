package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import com.ibm.safe.internal.exceptions.PropertiesException;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.util.CancelException;

import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

@SuppressWarnings("restriction")
public class StreamAnalyzer extends ASTVisitor {

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	private Set<EclipseProjectAnalysisEngine<InstanceKey>> enginesWithBuiltCallGraphs = new HashSet<>();

	protected void buildCallGraph(EclipseProjectAnalysisEngine<InstanceKey> engine) throws IOException, CoreException,
			CallGraphBuilderCancelException, CancelException, InvalidClassFileException, NoEntryPointException {
		// if we haven't built the call graph yet.
		if (!enginesWithBuiltCallGraphs.contains(engine)) {
			// find the entry points.
			Set<Entrypoint> entryPoints = Util.findEntryPoints(engine.getClassHierarchy());

			if (entryPoints.isEmpty())
				throw new NoEntryPointException("Require Entry Point!");

			// set options.
			AnalysisOptions options = engine.getDefaultOptions(entryPoints);
			options.setReflectionOptions(ReflectionOptions.NONE);
			options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());

			try {
				engine.buildSafeCallGraph(options);
			} catch (IllegalStateException e) {
				LOGGER.log(Level.SEVERE, e,
						() -> "Exception encountered while building call graph for project: " + engine.getProject());
				throw e;
			}
			// TODO: Can I slice the graph so that only nodes relevant to the
			// instance in question are present?
			enginesWithBuiltCallGraphs.add(engine);
		}
	}

	private Set<Stream> streamSet = new HashSet<>();

	public StreamAnalyzer() {
	}

	public StreamAnalyzer(boolean visitDocTags) {
		super(visitDocTags);
	}

	public void analyze() throws CoreException {
		// collect the projects to be analyzed.
		Map<IJavaProject, Set<Stream>> projectToStreams = this.getStreamSet().stream()
				.collect(Collectors.groupingBy(Stream::getCreationJavaProject, Collectors.toSet()));

		// process each project.
		for (IJavaProject project : projectToStreams.keySet()) {
			// create the analysis engine for the project.
			EclipseProjectAnalysisEngine<InstanceKey> engine = null;
			try {
				engine = new EclipseProjectAnalysisEngine<>(project);
				engine.buildAnalysisScope();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "Could not create analysis engine for: " + project, e);
				throw new RuntimeException(e);
			}

			// build the call graph for the project.
			try {
				buildCallGraph(engine);
			} catch (NoEntryPointException e) {
				LOGGER.log(Level.WARNING, "Exception caught while processing: " + engine.getProject(), e);

				// add a status entry for each stream in the project
				for (Stream stream : projectToStreams.get(project))
					stream.addStatusEntry(PreconditionFailure.NO_ENTRY_POINT,
							"Project: " + engine.getProject() + " has no entry points.");
				return;
			} catch (IOException | CoreException | InvalidClassFileException | CancelException e) {
				LOGGER.log(Level.SEVERE, "Exception encountered while building call graph for: " + project + ".", e);
				throw new RuntimeException(e);
			}

			OrderingInference orderingInference = new OrderingInference(engine.getClassHierarchy());

			// infer the initial attributes of each stream in the project.
			for (Stream stream : projectToStreams.get(project)) {
				try {
					stream.inferInitialAttributes(engine, orderingInference);
				} catch (InvalidClassFileException | IOException e) {
					LOGGER.log(Level.SEVERE, "Exception encountered while processing: " + stream.getCreation() + ".",
							e);
					throw new RuntimeException(e);
				}
			}

			// start the state machine for each stream in the project.
			StreamStateMachine stateMachine = new StreamStateMachine();
			try {
				stateMachine.start(streamSet, engine, orderingInference);
			} catch (PropertiesException | CancelException | NoniterableException | NoninstantiableException
					| CannotExtractSpliteratorException | InvalidClassFileException | IOException e) {
				LOGGER.log(Level.SEVERE, "Error while starting state machine.", e);
				throw new RuntimeException(e);
			}

			// check preconditions.
			for (Stream stream : projectToStreams.get(project))
				stream.check();
		} // end for each stream.
	}

	public Set<Stream> getStreamSet() {
		return streamSet;
	}

	/**
	 * @see org.eclipse.jdt.core.dom.ASTVisitor#visit(org.eclipse.jdt.core.dom.MethodInvocation)
	 */
	@Override
	public boolean visit(MethodInvocation node) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		ITypeBinding returnType = methodBinding.getReturnType();
		boolean returnTypeImplementsBaseStream = Util.implementsBaseStream(returnType);

		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		boolean declaringClassImplementsBaseStream = Util.implementsBaseStream(declaringClass);

		// Try to limit the analyzed methods to those of the API. In other
		// words, don't process methods returning streams that are declared in
		// the client application. TODO: This could be problematic if the API
		// implementation treats itself as a "client."
		String[] declaringClassPackageNameComponents = declaringClass.getPackage().getNameComponents();
		boolean isFromAPI = declaringClassPackageNameComponents.length > 0
				&& declaringClassPackageNameComponents[0].equals("java");

		boolean instanceMethod = !JdtFlags.isStatic(methodBinding);
		boolean intermediateOperation = instanceMethod && declaringClassImplementsBaseStream;

		// java.util.stream.BaseStream is the top-level interface for all
		// streams. Make sure we don't include intermediate operations.
		if (returnTypeImplementsBaseStream && !intermediateOperation && isFromAPI) {
			Stream stream = null;
			try {
				stream = new Stream(node);
			} catch (ClassHierarchyException | IOException | CoreException | InvalidClassFileException
					| CancelException e) {
				LOGGER.log(Level.SEVERE, "Encountered exception while processing: " + node, e);
				throw new RuntimeException(e);
			}
			this.getStreamSet().add(stream);
		}

		return super.visit(node);
	}
}
