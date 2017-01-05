package edu.cuny.hunter.streamrefactoring.core.refactorings;

import static org.eclipse.jdt.ui.JavaElementLabels.ALL_FULLY_QUALIFIED;
import static org.eclipse.jdt.ui.JavaElementLabels.getElementLabel;
import static org.eclipse.jdt.ui.JavaElementLabels.getTextLabel;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRewriteUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.ReferenceFinderUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.TypeVariableMaplet;
import org.eclipse.jdt.internal.corext.refactoring.structure.HierarchyProcessor.TypeVariableMapper;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.TextEditBasedChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.viewsupport.BasicElementLabels;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.GroupCategorySet;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.osgi.framework.FrameworkUtil;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.cuny.hunter.streamrefactoring.core.descriptors.ConvertStreamToParallelRefactoringDescriptor;
import edu.cuny.hunter.streamrefactoring.core.messages.Messages;
import edu.cuny.hunter.streamrefactoring.core.messages.PreconditionFailure;
import edu.cuny.hunter.streamrefactoring.core.utils.RefactoringAvailabilityTester;
import edu.cuny.hunter.streamrefactoring.core.utils.TimeCollector;
import edu.cuny.hunter.streamrefactoring.core.utils.TypeVariableUtil;
import edu.cuny.hunter.streamrefactoring.core.utils.Util;

/**
 * The activator class controls the plug-in life cycle
 * 
 * @author <a href="mailto:rkhatchadourian@citytech.cuny.edu">Raffi
 *         Khatchadourian</a>
 */
@SuppressWarnings({ "restriction" })
public class ConvertToParallelStreamRefactoringProcessor extends RefactoringProcessor {

	private final class SourceMethodBodyAnalysisVisitor extends ASTVisitor {
		private boolean methodContainsSuperReference;
		private boolean methodContainsCallToProtectedObjectMethod;
		private boolean methodContainsTypeIncompatibleThisReference;
		private boolean methodContainsQualifiedThisExpression;
		private Set<IMethod> calledProtectedObjectMethodSet = new HashSet<>();
		private IMethod sourceMethod;
		private Optional<IProgressMonitor> monitor;

		public SourceMethodBodyAnalysisVisitor(IMethod sourceMethod, Optional<IProgressMonitor> monitor) {
			super(false);
			this.sourceMethod = sourceMethod;
			this.monitor = monitor;
		}

		protected Set<IMethod> getCalledProtectedObjectMethodSet() {
			return calledProtectedObjectMethodSet;
		}

		@Override
		public boolean visit(SuperConstructorInvocation node) {
			this.methodContainsSuperReference = true;
			return super.visit(node);
		}

		protected boolean doesMethodContainsSuperReference() {
			return methodContainsSuperReference;
		}

		protected boolean doesMethodContainsCallToProtectedObjectMethod() {
			return methodContainsCallToProtectedObjectMethod;
		}

		protected boolean doesMethodContainsTypeIncompatibleThisReference() {
			return methodContainsTypeIncompatibleThisReference;
		}

		protected boolean doesMethodContainQualifiedThisExpression() {
			return methodContainsQualifiedThisExpression;
		}

		@Override
		public boolean visit(SuperFieldAccess node) {
			this.methodContainsSuperReference = true;
			return super.visit(node);
		}

		@Override
		public boolean visit(SuperMethodInvocation node) {
			this.methodContainsSuperReference = true;
			return super.visit(node);
		}

		@Override
		public boolean visit(SuperMethodReference node) {
			this.methodContainsSuperReference = true;
			return super.visit(node);
		}

		@Override
		public boolean visit(MethodInvocation node) {
			// check for calls to particular java.lang.Object
			// methods #144.
			IMethodBinding methodBinding = node.resolveMethodBinding();

			if (methodBinding != null
					&& methodBinding.getDeclaringClass().getQualifiedName().equals("java.lang.Object")) {
				IMethod calledObjectMethod = (IMethod) methodBinding.getJavaElement();

				try {
					if (Flags.isProtected(calledObjectMethod.getFlags())) {
						this.methodContainsCallToProtectedObjectMethod = true;
						this.calledProtectedObjectMethodSet.add(calledObjectMethod);
					}
				} catch (JavaModelException e) {
					throw new RuntimeException(e);
				}
			}
			return super.visit(node);
		}

		@Override
		public boolean visit(ThisExpression node) {
			// #153: Precondition missing for compile-time type of this
			// TODO: #153 There is actually a lot more checks we should add
			// here.
			/*
			 * TODO: Actually need to examine every kind of expression where
			 * `this` may appear. #149. Really, type constraints can (or should)
			 * be used for this. Actually, similar to enum problem, especially
			 * with finding the parameter from where the `this` expression came.
			 * Assignment is only one kind of expression, we need to also look
			 * at comparison and switches.
			 */
			if (node.getQualifier() != null)
				this.methodContainsQualifiedThisExpression = true;

			ASTNode parent = node.getParent();
			process(parent, node);
			return super.visit(node);
		}

		private void process(ASTNode node, ThisExpression thisExpression) {
			switch (node.getNodeType()) {
			case ASTNode.METHOD_INVOCATION:
			case ASTNode.CLASS_INSTANCE_CREATION:
			case ASTNode.CONSTRUCTOR_INVOCATION:
				// NOTE: super isn't allowed inside the source method body.
			case ASTNode.ASSIGNMENT:
			case ASTNode.RETURN_STATEMENT:
			case ASTNode.VARIABLE_DECLARATION_FRAGMENT: {
				// get the target method.
				IMethod targetMethod = null;
				try {
					targetMethod = getTargetMethod(this.sourceMethod,
							this.monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN)));
				} catch (JavaModelException e) {
					throw new RuntimeException(e);
				}
				IType destinationInterface = targetMethod.getDeclaringType();

				// get the destination interface.
				ITypeBinding destinationInterfaceTypeBinding = null;
				try {
					destinationInterfaceTypeBinding = ASTNodeSearchUtil.getTypeDeclarationNode(destinationInterface,
							getCompilationUnit(destinationInterface.getTypeRoot(), new SubProgressMonitor(
									this.monitor.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN)))
							.resolveBinding();
				} catch (JavaModelException e) {
					throw new RuntimeException(e);
				}
				if (node.getNodeType() == ASTNode.CONSTRUCTOR_INVOCATION) {
					ConstructorInvocation constructorInvocation = (ConstructorInvocation) node;
					List<?> arguments = constructorInvocation.arguments();
					IMethodBinding methodBinding = constructorInvocation.resolveConstructorBinding();
					processArguments(arguments, methodBinding, thisExpression, destinationInterfaceTypeBinding);
				} else if (node.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION) {
					ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) node;
					List<?> arguments = classInstanceCreation.arguments();
					IMethodBinding methodBinding = classInstanceCreation.resolveConstructorBinding();
					processArguments(arguments, methodBinding, thisExpression, destinationInterfaceTypeBinding);
				} else if (node.getNodeType() == ASTNode.METHOD_INVOCATION) {
					MethodInvocation methodInvocation = (MethodInvocation) node;
					List<?> arguments = methodInvocation.arguments();
					IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();
					processArguments(arguments, methodBinding, thisExpression, destinationInterfaceTypeBinding);
				} else if (node.getNodeType() == ASTNode.ASSIGNMENT) {
					Assignment assignment = (Assignment) node;
					Expression leftHandSide = assignment.getLeftHandSide();
					Expression rightHandSide = assignment.getRightHandSide();
					processAssignment(assignment, thisExpression, destinationInterfaceTypeBinding, leftHandSide,
							rightHandSide);
				} else if (node.getNodeType() == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
					VariableDeclarationFragment vdf = (VariableDeclarationFragment) node;
					Expression initializer = vdf.getInitializer();
					SimpleName name = vdf.getName();
					processAssignment(vdf, thisExpression, destinationInterfaceTypeBinding, name, initializer);
				} else if (node.getNodeType() == ASTNode.RETURN_STATEMENT) {
					ReturnStatement returnStatement = (ReturnStatement) node;

					// sanity check.
					Expression expression = returnStatement.getExpression();
					expression = (Expression) Util.stripParenthesizedExpressions(expression);
					Assert.isTrue(expression == thisExpression, "The return expression should be this.");

					MethodDeclaration targetMethodDeclaration = null;
					try {
						targetMethodDeclaration = ASTNodeSearchUtil.getMethodDeclarationNode(targetMethod,
								getCompilationUnit(targetMethod.getTypeRoot(), new SubProgressMonitor(
										this.monitor.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN)));
					} catch (JavaModelException e) {
						throw new RuntimeException(e);
					}
					ITypeBinding returnType = targetMethodDeclaration.resolveBinding().getReturnType();

					// ensure that the destination type is assignment compatible
					// with the return type.
					if (!isAssignmentCompatible(destinationInterfaceTypeBinding, returnType))
						this.methodContainsTypeIncompatibleThisReference = true;
				} else
					throw new IllegalStateException("Unexpected node type: " + node.getNodeType());
				break;
			}
			case ASTNode.PARENTHESIZED_EXPRESSION: {
				process(node.getParent(), thisExpression);
			}
			}
		}

		/**
		 * Process a list of arguments stemming from a method-like call.
		 * 
		 * @param arguments
		 *            The list of arguments to process.
		 * @param methodBinding
		 *            The binding of the corresponding method call.
		 * @param thisExpression
		 *            The this expression we are looking for.
		 * @param destinationInterfaceTypeBinding
		 *            The binding of the destination interface.
		 */
		private void processArguments(List<?> arguments, IMethodBinding methodBinding, ThisExpression thisExpression,
				ITypeBinding destinationInterfaceTypeBinding) {
			// find where (or if) the this expression occurs in the
			// method
			// invocation arguments.
			for (int i = 0; i < arguments.size(); i++) {
				Object object = arguments.get(i);
				// if we are at the argument where this appears.
				if (object == thisExpression) {
					// get the type binding from the corresponding
					// parameter.
					ITypeBinding parameterTypeBinding;

					// varargs case.
					// if we are at or past the last parameter and the method is
					// vararg.
					if (i >= methodBinding.getParameterTypes().length - 1 && methodBinding.isVarargs()) {
						// assign the parameter type binding to be the scalar
						// type of the last parameter type.
						parameterTypeBinding = methodBinding
								.getParameterTypes()[methodBinding.getParameterTypes().length - 1].getElementType();
						Assert.isNotNull(parameterTypeBinding,
								"The last parameter of a vararg method should be an array.");
					} else
						parameterTypeBinding = methodBinding.getParameterTypes()[i];

					// the type of this will change to the destination
					// interface. Let's check whether an expression of
					// the destination type can be assigned to a
					// variable of
					// the parameter type.
					// TODO: Does `isAssignmentCompatible()` also work
					// with
					// comparison?
					if (!isAssignmentCompatible(destinationInterfaceTypeBinding, parameterTypeBinding)) {
						this.methodContainsTypeIncompatibleThisReference = true;
						break;
					}
				}
			}
		}

		private void processAssignment(ASTNode node, ThisExpression thisExpression,
				ITypeBinding destinationInterfaceTypeBinding, Expression leftHandSide, Expression rightHandSide) {
			// if `this` appears on the LHS.
			if (leftHandSide == thisExpression) {
				// in this case, we need to check that the RHS can be
				// assigned to a variable of the destination type.
				if (!isAssignmentCompatible(rightHandSide.resolveTypeBinding(), destinationInterfaceTypeBinding))
					this.methodContainsTypeIncompatibleThisReference = true;
			} else if (rightHandSide == thisExpression) {
				// otherwise, if `this` appears on the RHS. Then, we
				// need to check that the LHS can receive a variable of
				// the destination type.
				if (!isAssignmentCompatible(destinationInterfaceTypeBinding, leftHandSide.resolveTypeBinding()))
					this.methodContainsTypeIncompatibleThisReference = true;
			} else {
				throw new IllegalStateException(
						"this: " + thisExpression + " must appear either on the LHS or RHS of the assignment: " + node);
			}
		}
	}

	private final class FieldAccessAnalysisSearchRequestor extends SearchRequestor {
		private final Optional<IProgressMonitor> monitor;
		private boolean accessesFieldsFromImplicitParameter;

		private FieldAccessAnalysisSearchRequestor(Optional<IProgressMonitor> monitor) {
			this.monitor = monitor;
		}

		@Override
		public void acceptSearchMatch(SearchMatch match) throws CoreException {
			if (match.isInsideDocComment())
				return;

			// get the AST node corresponding to the field
			// access. It should be some kind of name
			// (simple of qualified).
			ASTNode node = ASTNodeSearchUtil.getAstNode(match, getCompilationUnit(
					((IMember) match.getElement()).getTypeRoot(),
					new SubProgressMonitor(monitor.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN)));

			// examine the node's parent.
			ASTNode parent = node.getParent();

			switch (parent.getNodeType()) {
			case ASTNode.FIELD_ACCESS: {
				FieldAccess fieldAccess = (FieldAccess) parent;

				// the expression is the LHS of the
				// selection operator.
				Expression expression = fieldAccess.getExpression();

				if (expression == null || expression.getNodeType() == ASTNode.THIS_EXPRESSION)
					// either there is nothing on the LHS
					// or it's this, in which case we fail.
					this.accessesFieldsFromImplicitParameter = true;
				break;
			}
			case ASTNode.SUPER_FIELD_ACCESS: {
				// super will also tell us that it's an
				// instance field access of this.
				this.accessesFieldsFromImplicitParameter = true;
				break;
			}
			default: {
				// it must be an unqualified field access,
				// meaning that it's an instance field access of this.
				this.accessesFieldsFromImplicitParameter = true;
			}
			}
		}

		public boolean hasAccessesToFieldsFromImplicitParameter() {
			return accessesFieldsFromImplicitParameter;
		}
	}

	private final class MethodReceiverAnalysisVisitor extends ASTVisitor {
		private IMethod accessedMethod;
		private boolean encounteredThisReceiver;

		public boolean hasEncounteredThisReceiver() {
			return encounteredThisReceiver;
		}

		private MethodReceiverAnalysisVisitor(IMethod accessedMethod) {
			this.accessedMethod = accessedMethod;
		}

		@Override
		public boolean visit(MethodInvocation methodInvocation) {
			IMethodBinding methodBinding = methodInvocation.resolveMethodBinding();

			if (methodBinding != null) {
				IJavaElement javaElement = methodBinding.getJavaElement();

				if (javaElement == null)
					logWarning("Could not get Java element from binding: " + methodBinding + " while processing: "
							+ methodInvocation);
				else if (javaElement.equals(accessedMethod)) {
					Expression expression = methodInvocation.getExpression();
					expression = (Expression) Util.stripParenthesizedExpressions(expression);

					// FIXME: It's not really that the expression is a `this`
					// expression but that the type of the expression comes from
					// a
					// `this` expression. In other words, we may need to climb
					// the
					// AST.
					if (expression == null || expression.getNodeType() == ASTNode.THIS_EXPRESSION) {
						this.encounteredThisReceiver = true;
					}
				}
			}
			return super.visit(methodInvocation);
		}

		@Override
		public boolean visit(SuperMethodInvocation node) {
			IMethodBinding methodBinding = node.resolveMethodBinding();
			if (methodBinding != null) {
				if (methodBinding.getJavaElement().equals(accessedMethod))
				this.encounteredThisReceiver = true;
			}
			return super.visit(node);
		}
	}

	private Set<IMethod> sourceMethods = new LinkedHashSet<>();

	private Set<IMethod> unmigratableMethods = new UnmigratableMethodSet(sourceMethods);

	private static final String FUNCTIONAL_INTERFACE_ANNOTATION_NAME = "FunctionalInterface";

	private Map<ICompilationUnit, CompilationUnitRewrite> compilationUnitToCompilationUnitRewriteMap = new HashMap<>();

	private Map<ITypeRoot, CompilationUnit> typeRootToCompilationUnitMap = new HashMap<>();

	@SuppressWarnings("unused")
	private static final GroupCategorySet SET_MIGRATE_METHOD_IMPLEMENTATION_TO_INTERFACE = new GroupCategorySet(
			new GroupCategory("edu.cuny.hunter.streamrefactoring", //$NON-NLS-1$
					Messages.CategoryName, Messages.CategoryDescription));

	private static Map<IMethod, IMethod> methodToTargetMethodMap = new HashMap<>();

	/** The code generation settings, or <code>null</code> */
	private CodeGenerationSettings settings;

	/** Does the refactoring use a working copy layer? */
	private final boolean layer;

	private static Table<IMethod, IType, IMethod> methodTargetInterfaceTargetMethodTable = HashBasedTable.create();

	private SearchEngine searchEngine = new SearchEngine();

	/**
	 * For excluding AST parse time.
	 */
	private TimeCollector excludedTimeCollector = new TimeCollector();

	/**
	 * The minimum logging level, one of the constants in
	 * org.eclipse.core.runtime.IStatus.
	 */
	private static int loggingLevel = IStatus.WARNING;

	/**
	 * Creates a new refactoring with the given methods to refactor.
	 * 
	 * @param methods
	 *            The methods to refactor.
	 * @throws JavaModelException
	 */
	public ConvertToParallelStreamRefactoringProcessor(final IMethod[] methods,
			final CodeGenerationSettings settings, boolean layer, Optional<IProgressMonitor> monitor)
					throws JavaModelException {
		try {
			this.settings = settings;
			this.layer = layer;
			Collections.addAll(this.getSourceMethods(), methods);

			monitor.ifPresent(m -> m.beginTask("Finding target methods ...", methods.length));

			for (IMethod method : methods) {
				// this will populate the map if needed.
				getTargetMethod(method, monitor);
				monitor.ifPresent(m -> m.worked(1));
			}

		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	public ConvertToParallelStreamRefactoringProcessor(final IMethod[] methods,
			final CodeGenerationSettings settings, Optional<IProgressMonitor> monitor) throws JavaModelException {
		this(methods, settings, false, monitor);
	}

	public ConvertToParallelStreamRefactoringProcessor(Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		this(null, null, false, monitor);
	}

	public ConvertToParallelStreamRefactoringProcessor() throws JavaModelException {
		this(null, null, false, Optional.empty());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object[] getElements() {
		return getMigratableMethods().toArray();
	}

	public Set<IMethod> getMigratableMethods() {
		Set<IMethod> difference = new LinkedHashSet<>(this.getSourceMethods());
		difference.removeAll(this.getUnmigratableMethods());
		return difference;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		try {
			this.clearCaches();
			this.getExcludedTimeCollector().clear();

			if (this.getSourceMethods().isEmpty())
				return RefactoringStatus.createFatalErrorStatus(Messages.StreamsNotSpecified);
			else {
				RefactoringStatus status = new RefactoringStatus();

				pm.beginTask(Messages.CheckingPreconditions, this.getSourceMethods().size());

				for (IMethod sourceMethod : this.getSourceMethods()) {
					status.merge(checkDeclaringType(sourceMethod, Optional.of(new SubProgressMonitor(pm, 0))));
					status.merge(checkCandidateDestinationInterfaces(sourceMethod,
							Optional.of(new SubProgressMonitor(pm, 0))));

					pm.worked(1);
				}
				return status;
			}
		} catch (Exception e) {
			JavaPlugin.log(e);
			throw e;
		} finally {
			pm.done();
		}
	}

	private RefactoringStatus checkDestinationInterfaceTargetMethods(IMethod sourceMethod) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();

		logInfo("Checking destination interface target methods...");

		// Ensure that target methods are not already default methods.
		// For each method to move, add a warning if the associated target
		// method is already default.
		IMethod targetMethod = getTargetMethod(sourceMethod, Optional.empty());

		if (targetMethod != null) {
			int targetMethodFlags = targetMethod.getFlags();

			if (Flags.isDefaultMethod(targetMethodFlags)) {
				RefactoringStatusEntry entry = addError(status, sourceMethod,
						PreconditionFailure.TargetMethodIsAlreadyDefault, targetMethod);
				addUnmigratableMethod(sourceMethod, entry);
			}
		}
		return status;
	}

	private RefactoringStatus checkDestinationInterfaces(Optional<IProgressMonitor> monitor) throws JavaModelException {
		try {
			RefactoringStatus status = new RefactoringStatus();

			monitor.ifPresent(m -> m.beginTask("Checking destination interfaces ...", this.getSourceMethods().size()));

			for (IMethod sourceMethod : this.getSourceMethods()) {
				final Optional<IType> targetInterface = this.getDestinationInterface(sourceMethod);

				// Can't be empty.
				if (!targetInterface.isPresent()) {
					addErrorAndMark(status, PreconditionFailure.NoDestinationInterface, sourceMethod);
					return status;
				}

				// Must be a pure interface.
				if (!isPureInterface(targetInterface.get())) {
					RefactoringStatusEntry error = addError(status, sourceMethod,
							PreconditionFailure.DestinationTypeMustBePureInterface, targetInterface.get());
					addUnmigratableMethod(sourceMethod, error);
				}

				// Make sure it exists.
				RefactoringStatus existence = checkExistence(targetInterface.get(),
						PreconditionFailure.DestinationInterfaceDoesNotExist);
				status.merge(existence);
				if (!existence.isOK())
					addUnmigratableMethod(sourceMethod, existence.getEntryWithHighestSeverity());

				// Make sure we can write to it.
				RefactoringStatus writabilitiy = checkWritabilitiy(targetInterface.get(),
						PreconditionFailure.DestinationInterfaceNotWritable);
				status.merge(writabilitiy);
				if (!writabilitiy.isOK())
					addUnmigratableMethod(sourceMethod, writabilitiy.getEntryWithHighestSeverity());

				// Make sure it's not in a derived resource.
				if (targetInterface.get().getResource().isDerived(IResource.CHECK_ANCESTORS))
					addErrorAndMark(status, PreconditionFailure.DestinationInterfaceIsDerived, sourceMethod,
							targetInterface.get());

				// Make sure it doesn't have compilation errors.
				RefactoringStatus structure = checkStructure(targetInterface.get());
				status.merge(structure);
				if (!structure.isOK())
					addUnmigratableMethod(sourceMethod, structure.getEntryWithHighestSeverity());

				// #35: The target interface should not be a
				// @FunctionalInterface.
				if (isInterfaceFunctional(targetInterface.get())) {
					RefactoringStatusEntry error = addError(status, sourceMethod,
							PreconditionFailure.DestinationInterfaceIsFunctional, targetInterface.get());
					addUnmigratableMethod(sourceMethod, error);
				}

				// Can't be strictfp if all the methods to be migrated aren't
				// also strictfp #42.
				if (Flags.isStrictfp(targetInterface.get().getFlags())
						&& !allMethodsToMoveInTypeAreStrictFP(sourceMethod.getDeclaringType())) {
					RefactoringStatusEntry error = addError(status, sourceMethod,
							PreconditionFailure.DestinationInterfaceIsStrictFP, targetInterface.get());
					addUnmigratableMethod(sourceMethod, error);
				}

				status.merge(checkDestinationInterfaceTargetMethods(sourceMethod));

				monitor.ifPresent(m -> m.worked(1));
			}

			return status;
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	private IType[] getTypesReferencedInMovedMembers(IMethod sourceMethod, final Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		// TODO: Cache this result.
		final IType[] types = ReferenceFinderUtil.getTypesReferencedIn(new IJavaElement[] { sourceMethod },
				monitor.orElseGet(NullProgressMonitor::new));
		final List<IType> result = new ArrayList<IType>(types.length);
		final List<IMember> members = Arrays.asList(new IMember[] { sourceMethod });
		for (int index = 0; index < types.length; index++) {
			if (!members.contains(types[index]) && !types[index].equals(sourceMethod.getDeclaringType()))
				result.add(types[index]);
		}
		return result.toArray(new IType[result.size()]);
	}

	private boolean canBeAccessedFrom(IMethod sourceMethod, final IMember member, final IType target,
			final ITypeHierarchy hierarchy) throws JavaModelException {
		Assert.isTrue(!(member instanceof IInitializer));
		if (member.exists()) {
			if (target.equals(member.getDeclaringType()))
				return true;
			if (target.equals(member))
				return true;
			// NOTE: We are not creating stubs (for now).
			// Related to https://bugs.eclipse.org/bugs/show_bug.cgi?id=495439.
			/*
			 * if (member instanceof IMethod) { final IMethod method = (IMethod)
			 * member; final IMethod stub =
			 * target.getMethod(method.getElementName(),
			 * method.getParameterTypes()); if (stub.exists()) return true; }
			 */
			if (member.getDeclaringType() == null) {
				if (!(member instanceof IType))
					return false;
				if (JdtFlags.isPublic(member))
					return true;
				if (!JdtFlags.isPackageVisible(member))
					return false;
				if (JavaModelUtil.isSamePackage(((IType) member).getPackageFragment(), target.getPackageFragment()))
					return true;
				final IType type = member.getDeclaringType();
				if (type != null)
					return hierarchy.contains(type);
				return false;
			}
			final IType declaringType = member.getDeclaringType();
			// if the member's declaring type isn't accessible from the target
			// type.
			if (!canBeAccessedFrom(sourceMethod, declaringType, target, hierarchy))
				return false; // then, the member isn't accessible from the
								// target type.
			// otherwise, the member's declaring type is accessible from the
			// target type.
			// We are going to be moving the source method from it's
			// declaring type.
			// We know that the member's declaring type is accessible from
			// the target.
			// We also know that the member's declaring type and the target
			// type are different.
			// The question now is if the target type can access the
			// particular member given that
			// the target type can access the member's declaring type.
			// if it's public, the answer is yes.
			if (JdtFlags.isPublic(member))
				return true;
			// if the member is private, the answer is no.
			else if (JdtFlags.isPrivate(member))
				return false;
			// if it's package-private or protected.
			else if (JdtFlags.isPackageVisible(member) || JdtFlags.isProtected(member)) {
				// then, if the member's declaring type in the same package
				// as the target's declaring type, the answer is yes.
				if (JavaModelUtil.isVisible(member, target.getPackageFragment()))
					return true;
				// otherwise, if it's protected.
				else if (JdtFlags.isProtected(member))
					// then, the answer is yes if the target type is a
					// sub-type of the member's declaring type. Otherwise,
					// the answer is no.
					return hierarchy.contains(declaringType);
				else
					return false; // not accessible.
			} else
				throw new IllegalStateException("Member: " + member + " has no known visibility.");
		}
		return false;
	}

	private RefactoringStatus checkAccessedTypes(IMethod sourceMethod, final Optional<IProgressMonitor> monitor,
			final ITypeHierarchy hierarchy) throws JavaModelException {
		final RefactoringStatus result = new RefactoringStatus();
		final IType[] accessedTypes = getTypesReferencedInMovedMembers(sourceMethod, monitor);
		final IType destination = getDestinationInterface(sourceMethod).get();
		final List<IMember> pulledUpList = Arrays.asList(sourceMethod);
		for (int index = 0; index < accessedTypes.length; index++) {
			final IType type = accessedTypes[index];
			if (!type.exists())
				continue;

			if (!canBeAccessedFrom(sourceMethod, type, destination, hierarchy) && !pulledUpList.contains(type)) {
				final String message = org.eclipse.jdt.internal.corext.util.Messages.format(
						PreconditionFailure.TypeNotAccessible.getMessage(),
						new String[] { JavaElementLabels.getTextLabel(type, JavaElementLabels.ALL_FULLY_QUALIFIED),
								JavaElementLabels.getTextLabel(destination, JavaElementLabels.ALL_FULLY_QUALIFIED) });
				result.addEntry(RefactoringStatus.ERROR, message, JavaStatusContext.create(type),
						ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID,
						PreconditionFailure.TypeNotAccessible.ordinal(), sourceMethod);
				this.getUnmigratableMethods().add(sourceMethod);
			}
		}
		monitor.ifPresent(IProgressMonitor::done);
		return result;
	}

	private RefactoringStatus checkAccessedFields(IMethod sourceMethod, final Optional<IProgressMonitor> monitor,
			final ITypeHierarchy destinationInterfaceSuperTypeHierarchy) throws CoreException {
		monitor.ifPresent(m -> m.beginTask(RefactoringCoreMessages.PullUpRefactoring_checking_referenced_elements, 2));
		final RefactoringStatus result = new RefactoringStatus();

		final List<IMember> pulledUpList = Arrays.asList(sourceMethod);

		final IField[] accessedFields = ReferenceFinderUtil.getFieldsReferencedIn(new IJavaElement[] { sourceMethod },
				new SubProgressMonitor(monitor.orElseGet(NullProgressMonitor::new), 1));

		final IType destination = getDestinationInterface(sourceMethod).orElseThrow(() -> new IllegalArgumentException(
				"Source method: " + sourceMethod + " has no destiantion interface."));

		for (int index = 0; index < accessedFields.length; index++) {
			final IField accessedField = accessedFields[index];

			if (!accessedField.exists())
				continue;

			boolean isAccessible = pulledUpList.contains(accessedField) || canBeAccessedFrom(sourceMethod,
					accessedField, destination, destinationInterfaceSuperTypeHierarchy)
					|| Flags.isEnum(accessedField.getFlags());

			if (!isAccessible) {
				final String message = org.eclipse.jdt.internal.corext.util.Messages.format(
						PreconditionFailure.FieldNotAccessible.getMessage(),
								new String[] {
								JavaElementLabels.getTextLabel(accessedField, JavaElementLabels.ALL_FULLY_QUALIFIED),
								JavaElementLabels.getTextLabel(destination, JavaElementLabels.ALL_FULLY_QUALIFIED) });
				result.addEntry(RefactoringStatus.ERROR, message, JavaStatusContext.create(accessedField),
						ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID,
						PreconditionFailure.FieldNotAccessible.ordinal(), sourceMethod);
				this.getUnmigratableMethods().add(sourceMethod);
			} else if (!JdtFlags.isStatic(accessedField) && !accessedField.getDeclaringType().isInterface()) {
				// it's accessible and it's an instance field.
				// Let's decide if the source method is accessing it from this
				// object or another. If it's from this object, we fail.
				// First, find all references of the accessed field in the
				// source method.
				FieldAccessAnalysisSearchRequestor requestor = new FieldAccessAnalysisSearchRequestor(monitor);
				this.getSearchEngine().search(
						SearchPattern.createPattern(accessedField, IJavaSearchConstants.REFERENCES,
								SearchPattern.R_EXACT_MATCH),
						new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
						SearchEngine.createJavaSearchScope(new IJavaElement[] { sourceMethod }), requestor,
						new SubProgressMonitor(monitor.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN));

				if (requestor.hasAccessesToFieldsFromImplicitParameter())
					addErrorAndMark(result, PreconditionFailure.SourceMethodAccessesInstanceField, sourceMethod,
							accessedField);
			}
		}
		monitor.ifPresent(IProgressMonitor::done);
		return result;
	}

	private RefactoringStatus checkAccessedMethods(IMethod sourceMethod, final Optional<IProgressMonitor> monitor,
			final ITypeHierarchy destinationInterfaceSuperTypeHierarchy) throws CoreException {
		monitor.ifPresent(m -> m.beginTask(RefactoringCoreMessages.PullUpRefactoring_checking_referenced_elements, 3));
		final RefactoringStatus result = new RefactoringStatus();

		final List<IMember> pulledUpList = Arrays.asList(sourceMethod);

		final Set<IMethod> accessedMethods = new LinkedHashSet<>(
				Arrays.asList(ReferenceFinderUtil.getMethodsReferencedIn(new IJavaElement[] { sourceMethod },
						new SubProgressMonitor(monitor.orElseGet(NullProgressMonitor::new), 1))));

		// also add constructors.
		accessedMethods.addAll(getConstructorsReferencedIn(new IJavaElement[] { sourceMethod },
				monitor.map(m -> new SubProgressMonitor(m, 1))));

		final IType destination = getDestinationInterface(sourceMethod).orElseThrow(() -> new IllegalArgumentException(
				"Source method: " + sourceMethod + " has no destiantion interface."));

		for (IMethod accessedMethod : accessedMethods) {
			if (!accessedMethod.exists())
				continue;

			boolean isAccessible = pulledUpList.contains(accessedMethod) || canBeAccessedFrom(sourceMethod,
					accessedMethod, destination, destinationInterfaceSuperTypeHierarchy);

			if (!isAccessible) {
				final String message = org.eclipse.jdt.internal.corext.util.Messages.format(
						PreconditionFailure.MethodNotAccessible.getMessage(),
						new String[] { getTextLabel(accessedMethod, ALL_FULLY_QUALIFIED),
								getTextLabel(destination, ALL_FULLY_QUALIFIED) });
				result.addEntry(RefactoringStatus.ERROR, message, JavaStatusContext.create(accessedMethod),
						ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID,
						PreconditionFailure.MethodNotAccessible.ordinal(), sourceMethod);
				this.getUnmigratableMethods().add(sourceMethod);
			} else if (!JdtFlags.isStatic(accessedMethod)) {
				// it's accessible and it's not static.
				// we'll need to check the implicit parameters.
				MethodDeclaration sourceMethodDeclaration = ASTNodeSearchUtil.getMethodDeclarationNode(sourceMethod,
						getCompilationUnit(sourceMethod.getTypeRoot(), new SubProgressMonitor(
								monitor.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN)));

				MethodReceiverAnalysisVisitor visitor = new MethodReceiverAnalysisVisitor(accessedMethod);
				sourceMethodDeclaration.getBody().accept(visitor);

				// if this is the implicit parameter.
				if (visitor.hasEncounteredThisReceiver()) {
					// let's check to see if the method is somewhere in the
					// hierarchy.
					IType methodDeclaringType = accessedMethod.getDeclaringType();

					// is this method declared in a type that is in the
					// declaring type's super type hierarchy?
					ITypeHierarchy declaringTypeSuperTypeHierarchy = getSuperTypeHierarchy(
							sourceMethod.getDeclaringType(),
							monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN)));

					if (declaringTypeSuperTypeHierarchy.contains(methodDeclaringType)) {
						// if so, then we need to check that it is in the
						// destination interface's super type hierarchy.
						boolean methodInHiearchy = isMethodInHierarchy(accessedMethod,
								destinationInterfaceSuperTypeHierarchy);
						if (!methodInHiearchy) {
							final String message = org.eclipse.jdt.internal.corext.util.Messages.format(
									PreconditionFailure.MethodNotAccessible.getMessage(),
									new String[] { getTextLabel(accessedMethod, ALL_FULLY_QUALIFIED),
											getTextLabel(destination, ALL_FULLY_QUALIFIED) });
							result.addEntry(RefactoringStatus.ERROR, message, JavaStatusContext.create(accessedMethod),
									ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID,
									PreconditionFailure.MethodNotAccessible.ordinal(), sourceMethod);
							this.getUnmigratableMethods().add(sourceMethod);
						}
					}
				}
			}
		}

		monitor.ifPresent(IProgressMonitor::done);
		return result;
	}

	private Collection<? extends IMethod> getConstructorsReferencedIn(IJavaElement[] elements,
			final Optional<IProgressMonitor> monitor) throws CoreException {
		Collection<IMethod> ret = new LinkedHashSet<>();

		SearchPattern pattern = SearchPattern.createPattern("*", IJavaSearchConstants.CONSTRUCTOR,
				IJavaSearchConstants.REFERENCES, SearchPattern.R_PATTERN_MATCH);
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(elements, true);
		SearchParticipant[] participants = new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() };

		this.getSearchEngine().search(pattern, participants, scope, new SearchRequestor() {

			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException {
				if (match.isInsideDocComment())
					return;

				ASTNode node = ASTNodeSearchUtil.getAstNode(match, getCompilationUnit(
						((IMember) match.getElement()).getTypeRoot(),
						new SubProgressMonitor(monitor.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN)));

				node = Util.stripParenthesizedExpressions(node);
				IMethod constructor = extractConstructor(node);

				if (constructor != null)
					ret.add(constructor);
			}

			private IMethod extractConstructor(ASTNode node) {
				if (node == null)
					throw new IllegalArgumentException("Node is null");
				else {
					switch (node.getNodeType()) {
					case ASTNode.CLASS_INSTANCE_CREATION: {
						ClassInstanceCreation creation = (ClassInstanceCreation) node;
						IMethodBinding binding = creation.resolveConstructorBinding();
						return binding == null ? null : (IMethod) binding.getJavaElement();
					}
					case ASTNode.CONSTRUCTOR_INVOCATION: {
						ConstructorInvocation invocation = (ConstructorInvocation) node;
						IMethodBinding binding = invocation.resolveConstructorBinding();
						return binding == null ? null : (IMethod) binding.getJavaElement();
					}
					case ASTNode.SUPER_CONSTRUCTOR_INVOCATION: {
						SuperConstructorInvocation invocation = (SuperConstructorInvocation) node;
						IMethodBinding binding = invocation.resolveConstructorBinding();
						return binding == null ? null : (IMethod) binding.getJavaElement();
					}
					case ASTNode.CREATION_REFERENCE: {
						CreationReference reference = (CreationReference) node;
						IMethodBinding binding = reference.resolveMethodBinding();

						if (binding == null) {
							logWarning("Could not resolve method binding from creation reference: " + reference);
							return null;
						}

						IMethod javaElement = (IMethod) binding.getJavaElement();
						return javaElement;
					}
					case ASTNode.TYPE_DECLARATION: {
						// no ctor reference here.
						return null;
					}
					default: {
						// try the parent node.
						logInfo("Getting parent of: " + node + ". Node type is: " + node.getNodeType());
						return extractConstructor(node.getParent());
					}
					}
				}
			}
		}, monitor.orElseGet(NullProgressMonitor::new));

		return ret;
	}

	private static boolean isMethodInHierarchy(IMethod method, ITypeHierarchy hierarchy) {
		// TODO: Cache this?
		return Stream.of(hierarchy.getAllTypes()).parallel().anyMatch(t -> {
			IMethod[] methods = t.findMethods(method);
			return methods != null && methods.length > 0;
		});
	}

	private RefactoringStatus checkAccesses(IMethod sourceMethod, final Optional<IProgressMonitor> monitor)
			throws CoreException {
		final RefactoringStatus result = new RefactoringStatus();
		try {
			monitor.ifPresent(
					m -> m.beginTask(RefactoringCoreMessages.PullUpRefactoring_checking_referenced_elements, 4));

			IType destinationInterface = getDestinationInterface(sourceMethod)
					.orElseThrow(() -> new IllegalArgumentException(
							"Source method: " + sourceMethod + " has no destination interface."));

			final ITypeHierarchy destinationInterfaceSuperTypeHierarchy = getSuperTypeHierarchy(destinationInterface,
					monitor.map(m -> new SubProgressMonitor(m, 1)));

			result.merge(checkAccessedTypes(sourceMethod, monitor.map(m -> new SubProgressMonitor(m, 1)),
					destinationInterfaceSuperTypeHierarchy));

			result.merge(checkAccessedFields(sourceMethod, monitor.map(m -> new SubProgressMonitor(m, 1)),
					destinationInterfaceSuperTypeHierarchy));

			result.merge(checkAccessedMethods(sourceMethod, monitor.map(m -> new SubProgressMonitor(m, 1)),
					destinationInterfaceSuperTypeHierarchy));
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
		return result;
	}

	private boolean allMethodsToMoveInTypeAreStrictFP(IType type) throws JavaModelException {
		for (Iterator<IMethod> iterator = this.getSourceMethods().iterator(); iterator.hasNext();) {
			IMethod method = iterator.next();
			if (method.getDeclaringType().equals(type) && !Flags.isStrictfp(method.getFlags()))
				return false;
		}
		return true;
	}

	private static boolean isInterfaceFunctional(final IType anInterface) throws JavaModelException {
		// TODO: #37: Compute effectively functional interfaces.
		return Stream.of(anInterface.getAnnotations()).parallel().map(IAnnotation::getElementName)
				.anyMatch(s -> s.contains(FUNCTIONAL_INTERFACE_ANNOTATION_NAME));
	}

	private RefactoringStatus checkValidInterfacesInDeclaringTypeHierarchy(IMethod sourceMethod,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();

		monitor.ifPresent(m -> m.beginTask("Checking valid interfaces in declaring type hierarchy ...",
				IProgressMonitor.UNKNOWN));
		try {
			ITypeHierarchy hierarchy = this.getDeclaringTypeHierarchy(sourceMethod, monitor);
			IType[] declaringTypeSuperInterfaces = hierarchy.getAllSuperInterfaces(sourceMethod.getDeclaringType());

			// the number of methods sourceMethod is implementing.
			long numberOfImplementedMethods = Arrays.stream(declaringTypeSuperInterfaces).parallel().distinct().flatMap(
					i -> Arrays.stream(Optional.ofNullable(i.findMethods(sourceMethod)).orElse(new IMethod[] {})))
					.count();

			if (numberOfImplementedMethods > 1)
				addErrorAndMark(status, PreconditionFailure.SourceMethodImplementsMultipleMethods, sourceMethod);

			// for each subclass of the declaring type.
			for (IType subclass : hierarchy.getSubclasses(sourceMethod.getDeclaringType())) {
				status.merge(checkClassForMissingSourceMethodImplementation(sourceMethod, subclass, hierarchy,
						monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN))));
			}
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
		return status;
	}

	/**
	 * Checks the given class and its subclasses for any required
	 * implementations of the source method. The required implementations are
	 * deemed by the interfaces the given class implements.
	 * 
	 * @param sourceMethod
	 *            The source method being migrated to an interface.
	 * @param clazz
	 *            The class to check for any missing needed implementations of
	 *            the source method.
	 * @param declaringTypeHierarchy
	 *            Hierarchy of the source method's declaring type.
	 * @return {@link RefactoringStatus} indicating the result of the
	 *         precondition check.
	 * @throws JavaModelException
	 *             When asserting that the given {@link IType} is indeed a
	 *             class.
	 */
	private RefactoringStatus checkClassForMissingSourceMethodImplementation(IMethod sourceMethod, IType clazz,
			ITypeHierarchy declaringTypeHierarchy, Optional<IProgressMonitor> monitor) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();

		// does the class have an implementation or declaration of the source
		// method?
		IMethod[] classMethodMatchingSourceMethod = clazz.findMethods(sourceMethod);
		if (classMethodMatchingSourceMethod != null && classMethodMatchingSourceMethod.length > 0)
			// in this case, the class has an implementation. No need to check
			// any interfaces or subclasses because any interfaces would be
			// satisfied and any unsatisfied interfaces will inherit the method
			// from this class.
			return status;
		// otherwise, no matching methods were found in the given class.
		else {
			// retrieve super interfaces of the class.
			IType[] superInterfaces = getSuperInterfaces(clazz, declaringTypeHierarchy,
					monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN)));

			try {
				monitor.ifPresent(m -> m.beginTask("Checking class for missing source method implementation ...",
						superInterfaces.length));

				// retrieve the destination interface.
				IType destinationInterface = getTargetMethod(sourceMethod,
						monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN))).getDeclaringType();

				// for each super interface of the given class.
				for (IType superInterface : superInterfaces) {
					// if it is not equal to the destination interface.
					if (!superInterface.equals(destinationInterface)) {
						IMethod[] interfaceMethodMatchingSourceMethod = superInterface.findMethods(sourceMethod);
						if (interfaceMethodMatchingSourceMethod != null
								&& interfaceMethodMatchingSourceMethod.length > 0)
							// there are multiple method definitions stemming
							// from interfaces. this class doesn't have an
							// implementation of the source method nor does it
							// inherit it.
							addErrorAndMark(status,
									PreconditionFailure.SourceMethodProvidesImplementationsForMultipleMethods,
									sourceMethod, superInterface);
					}
					monitor.ifPresent(m -> m.worked(1));
				}

				// check subclasses of the given class.
				for (IType subclass : declaringTypeHierarchy.getSubclasses(clazz))
					status.merge(checkClassForMissingSourceMethodImplementation(sourceMethod, subclass,
							declaringTypeHierarchy,
							monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN))));

				return status;
			} finally {
				monitor.ifPresent(IProgressMonitor::done);
			}
		}
	}

	/**
	 * Workaround
	 * org.eclipse.jdt.core.ITypeHierarchy.getAllSuperInterfaces(IType) does not
	 * seem to consider interface hierarchy
	 * {@linkplain https://bugs.eclipse.org/bugs/show_bug.cgi?id=496503}.
	 * 
	 * Returns the direct resolved interfaces that the given type implements or
	 * extends, in no particular order, limited to the interfaces in this type
	 * hierarchy's graph. For classes, this gives the interfaces that the class
	 * implements, as well as the interfaces that those interfaces extend. For
	 * interfaces, this gives the interfaces that the interface extends.
	 *
	 * @param type
	 *            the given type
	 * @param typeHierarchy
	 *            The type hierarchy to search.
	 * @param monitor
	 *            An optional progress monitor.
	 * @return the direct resolved interfaces that the given type implements
	 *         and/or extends limited to the interfaces in this type hierarchy's
	 *         graph.
	 * @throws JavaModelException
	 *             On a java model error.
	 * @see org.eclipse.jdt.core.ITypeHierarchy#getSuperInterfaces(IType)
	 */
	private static IType[] getSuperInterfaces(IType type, ITypeHierarchy typeHierarchy,
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		Set<IType> ret = new LinkedHashSet<>();

		IType[] superInterfaces = typeHierarchy.getSuperInterfaces(type);
		ret.addAll(Arrays.asList(superInterfaces));

		monitor.ifPresent(m -> m.beginTask("Retreiving super interfaces ...", superInterfaces.length));
		try {
			for (IType superInterface : superInterfaces) {
				ret.addAll(Arrays
						.asList(getSuperInterfaces(superInterface, monitor.map(m -> new SubProgressMonitor(m, 1)))));
			}
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}

		return ret.toArray(new IType[ret.size()]);
	}

	private Optional<IType> getDestinationInterface(IMethod sourceMethod) throws JavaModelException {
		return Optional.ofNullable(getTargetMethod(sourceMethod, Optional.empty())).map(IMethod::getDeclaringType);
	}

	private RefactoringStatus checkValidClassesInDeclaringTypeHierarchy(IMethod sourceMethod,
			final ITypeHierarchy declaringTypeHierarchy) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();

		IType[] allDeclaringTypeSuperclasses = declaringTypeHierarchy
				.getAllSuperclasses(sourceMethod.getDeclaringType());

		// is the source method overriding anything in the declaring type
		// hierarchy? If so, don't allow the refactoring to proceed #107.
		if (Stream.of(allDeclaringTypeSuperclasses).parallel().anyMatch(c -> {
			IMethod[] methods = c.findMethods(sourceMethod);
			return methods != null && methods.length > 0;
		}))
			addErrorAndMark(status, PreconditionFailure.SourceMethodOverridesMethod, sourceMethod);

		return status;
	}

	private void addWarning(RefactoringStatus status, IMethod sourceMethod, PreconditionFailure failure) {
		addWarning(status, sourceMethod, failure, new IJavaElement[] {});
	}

	private RefactoringStatus checkDeclaringType(IMethod sourceMethod, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();
		IType type = sourceMethod.getDeclaringType();

		if (type.isAnnotation())
			addErrorAndMark(status, PreconditionFailure.NoMethodsInAnnotationTypes, sourceMethod, type);
		if (type.isInterface())
			addErrorAndMark(status, PreconditionFailure.NoMethodsInInterfaces, sourceMethod, type);
		if (type.isBinary())
			addErrorAndMark(status, PreconditionFailure.NoMethodsInBinaryTypes, sourceMethod, type);
		if (type.isReadOnly())
			addErrorAndMark(status, PreconditionFailure.NoMethodsInReadOnlyTypes, sourceMethod, type);
		if (type.isAnonymous())
			addErrorAndMark(status, PreconditionFailure.NoMethodsInAnonymousTypes, sourceMethod, type);
		if (type.isLambda())
			// TODO for now.
			addErrorAndMark(status, PreconditionFailure.NoMethodsInLambdas, sourceMethod, type);
		if (type.isLocal())
			// TODO for now.
			addErrorAndMark(status, PreconditionFailure.NoMethodsInLocals, sourceMethod, type);

		// TODO enclosing type must implement an interface, at least for
		// now,
		// which one of which will become the target interface.
		// it is probably possible to still perform the refactoring
		// without this condition but I believe that this is
		// the particular pattern we are targeting.

		status.merge(checkDeclaringTypeHierarchy(sourceMethod, monitor.map(m -> new SubProgressMonitor(m, 1))));

		return status;
	}

	private void addErrorAndMark(RefactoringStatus status, PreconditionFailure failure, IMethod sourceMethod,
			IMember... related) {
		RefactoringStatusEntry error = addError(status, sourceMethod, failure, sourceMethod, related);
		addUnmigratableMethod(sourceMethod, error);
	}

	private RefactoringStatus checkDeclaringTypeHierarchy(IMethod sourceMethod, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		try {
			RefactoringStatus status = new RefactoringStatus();
			monitor.ifPresent(m -> m.subTask("Checking declaring type hierarchy..."));

			final ITypeHierarchy declaringTypeHierarchy = this.getDeclaringTypeHierarchy(sourceMethod, monitor);

			status.merge(checkValidClassesInDeclaringTypeHierarchy(sourceMethod, declaringTypeHierarchy));
			status.merge(checkValidInterfacesInDeclaringTypeHierarchy(sourceMethod, monitor));

			return status;
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	private RefactoringStatus checkCandidateDestinationInterfaces(IMethod sourceMethod,
			final Optional<IProgressMonitor> monitor) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();

		IType[] interfaces = getCandidateDestinationInterfaces(sourceMethod,
				monitor.map(m -> new SubProgressMonitor(m, 1)));

		if (interfaces.length == 0)
			addErrorAndMark(status, PreconditionFailure.NoMethodsInTypesWithNoCandidateTargetTypes, sourceMethod,
					sourceMethod.getDeclaringType());
		else if (interfaces.length > 1)
			// TODO: For now, let's make sure there's only one candidate type
			// #129.
			addErrorAndMark(status, PreconditionFailure.NoMethodsInTypesWithMultipleCandidateTargetTypes, sourceMethod,
					sourceMethod.getDeclaringType());

		return status;
	}

	/**
	 * Returns the possible target interfaces for the migration. NOTE: One
	 * difference here between this refactoring and pull up is that we can have
	 * a much more complex type hierarchy due to multiple interface inheritance
	 * in Java.
	 * <p>
	 * TODO: It should be possible to pull up a method into an interface (i.e.,
	 * "Pull Up Method To Interface") that is not implemented explicitly. For
	 * example, there may be a skeletal implementation class that implements all
	 * the target interface's methods without explicitly declaring so.
	 * Effectively skeletal?
	 * 
	 * @param monitor
	 *            A progress monitor.
	 * @return The possible target interfaces for the migration.
	 * @throws JavaModelException
	 *             upon Java model problems.
	 */
	public static IType[] getCandidateDestinationInterfaces(IMethod sourcMethod,
			final Optional<IProgressMonitor> monitor) throws JavaModelException {
		try {
			monitor.ifPresent(m -> m.beginTask("Retrieving candidate types...", IProgressMonitor.UNKNOWN));

			IType[] superInterfaces = getSuperInterfaces(sourcMethod.getDeclaringType(),
					monitor.map(m -> new SubProgressMonitor(m, 1)));

			Stream<IType> candidateStream = Stream.of(superInterfaces).parallel().filter(Objects::nonNull)
					.filter(IJavaElement::exists).filter(t -> !t.isReadOnly()).filter(t -> !t.isBinary());

			Set<IType> ret = new HashSet<>();

			for (Iterator<IType> iterator = candidateStream.iterator(); iterator.hasNext();) {
				IType superInterface = iterator.next();
				IMethod[] interfaceMethods = superInterface.findMethods(sourcMethod);
				if (interfaceMethods != null)
					// the matching methods cannot already be default.
					for (IMethod method : interfaceMethods)
						if (!JdtFlags.isDefaultMethod(method))
							ret.add(superInterface);
			}

			return ret.toArray(new IType[ret.size()]);
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	private static IType[] getSuperInterfaces(IType type, final Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		try {
			monitor.ifPresent(m -> m.beginTask("Retrieving type super interfaces...", IProgressMonitor.UNKNOWN));
			return getSuperTypeHierarchy(type, monitor.map(m -> new SubProgressMonitor(m, 1)))
					.getAllSuperInterfaces(type);
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	private ITypeHierarchy getDeclaringTypeHierarchy(IMethod sourceMethod, final Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		try {
			monitor.ifPresent(m -> m.subTask("Retrieving declaring type hierarchy..."));
			IType declaringType = sourceMethod.getDeclaringType();
			return this.getTypeHierarchy(declaringType, monitor);
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	@SuppressWarnings("unused")
	private ITypeHierarchy getDestinationInterfaceHierarchy(IMethod sourceMethod,
			final Optional<IProgressMonitor> monitor) throws JavaModelException {
		try {
			monitor.ifPresent(m -> m.subTask("Retrieving destination type hierarchy..."));
			IType destinationInterface = getDestinationInterface(sourceMethod).get();
			return this.getTypeHierarchy(destinationInterface, monitor);
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	private static Map<IType, ITypeHierarchy> typeToSuperTypeHierarchyMap = new HashMap<>();

	private static Map<IType, ITypeHierarchy> getTypeToSuperTypeHierarchyMap() {
		return typeToSuperTypeHierarchyMap;
	}

	public static ITypeHierarchy getSuperTypeHierarchy(IType type, final Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		try {
			monitor.ifPresent(m -> m.subTask("Retrieving declaring super type hierarchy..."));

			if (getTypeToSuperTypeHierarchyMap().containsKey(type))
				return getTypeToSuperTypeHierarchyMap().get(type);
			else {
				ITypeHierarchy newSupertypeHierarchy = type
						.newSupertypeHierarchy(monitor.orElseGet(NullProgressMonitor::new));
				getTypeToSuperTypeHierarchyMap().put(type, newSupertypeHierarchy);
				return newSupertypeHierarchy;
			}
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	private RefactoringStatus checkSourceMethods(Optional<IProgressMonitor> pm) throws CoreException {
		try {
			RefactoringStatus status = new RefactoringStatus();
			pm.ifPresent(m -> m.beginTask(Messages.CheckingPreconditions, this.getSourceMethods().size()));

			for (IMethod sourceMethod : this.getSourceMethods()) {
				// Does the source method exist?
				RefactoringStatus existenceStatus = checkExistence(sourceMethod,
						PreconditionFailure.MethodDoesNotExist);
				if (!existenceStatus.isOK()) {
					status.merge(existenceStatus);
					addUnmigratableMethod(sourceMethod, existenceStatus.getEntryWithHighestSeverity());
				}

				RefactoringStatus writabilityStatus = checkWritabilitiy(sourceMethod,
						PreconditionFailure.CantChangeMethod);
				if (!writabilityStatus.isOK()) {
					status.merge(writabilityStatus);
					addUnmigratableMethod(sourceMethod, writabilityStatus.getEntryWithHighestSeverity());
				}

				RefactoringStatus structureStatus = checkStructure(sourceMethod);
				if (!structureStatus.isOK()) {
					status.merge(structureStatus);
					addUnmigratableMethod(sourceMethod, structureStatus.getEntryWithHighestSeverity());
				}

				// the source method should not be included in a derived
				// resource.
				if (sourceMethod.getResource().isDerived(IResource.CHECK_ANCESTORS))
					addErrorAndMark(status, PreconditionFailure.SourceMethodIsDerived, sourceMethod);

				if (sourceMethod.isConstructor()) {
					RefactoringStatusEntry entry = addError(status, sourceMethod, PreconditionFailure.NoConstructors,
							sourceMethod);
					addUnmigratableMethod(sourceMethod, entry);
				}

				status.merge(checkAnnotations(sourceMethod));

				// synchronized methods aren't allowed in interfaces (even
				// if they're default).
				if (Flags.isSynchronized(sourceMethod.getFlags())) {
					RefactoringStatusEntry entry = addError(status, sourceMethod,
							PreconditionFailure.NoSynchronizedMethods, sourceMethod);
					addUnmigratableMethod(sourceMethod, entry);
				}
				if (Flags.isStatic(sourceMethod.getFlags())) {
					RefactoringStatusEntry entry = addError(status, sourceMethod, PreconditionFailure.NoStaticMethods,
							sourceMethod);
					addUnmigratableMethod(sourceMethod, entry);
				}
				if (Flags.isAbstract(sourceMethod.getFlags())) {
					RefactoringStatusEntry entry = addError(status, sourceMethod, PreconditionFailure.NoAbstractMethods,
							sourceMethod);
					addUnmigratableMethod(sourceMethod, entry);
				}
				// final methods aren't allowed in interfaces.
				if (Flags.isFinal(sourceMethod.getFlags())) {
					RefactoringStatusEntry entry = addError(status, sourceMethod, PreconditionFailure.NoFinalMethods,
							sourceMethod);
					addUnmigratableMethod(sourceMethod, entry);
				}
				// native methods don't have bodies. As such, they can't
				// be skeletal implementors.
				if (JdtFlags.isNative(sourceMethod)) {
					RefactoringStatusEntry entry = addError(status, sourceMethod, PreconditionFailure.NoNativeMethods,
							sourceMethod);
					addUnmigratableMethod(sourceMethod, entry);
				}
				if (sourceMethod.isLambdaMethod()) {
					RefactoringStatusEntry entry = addError(status, sourceMethod, PreconditionFailure.NoLambdaMethods,
							sourceMethod);
					addUnmigratableMethod(sourceMethod, entry);
				}

				status.merge(checkExceptions(sourceMethod));

				// ensure that the method has a target.
				if (getTargetMethod(sourceMethod,
						pm.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN))) == null)
					addErrorAndMark(status, PreconditionFailure.SourceMethodHasNoTargetMethod, sourceMethod);
				else {
					status.merge(checkParameters(sourceMethod, pm.map(m -> new SubProgressMonitor(m, 1))));
					status.merge(checkReturnType(sourceMethod, pm.map(m -> new SubProgressMonitor(m, 1))));
					status.merge(checkAccesses(sourceMethod, pm.map(m -> new SubProgressMonitor(m, 1))));
					status.merge(checkGenericDeclaringType(sourceMethod, pm.map(m -> new SubProgressMonitor(m, 1))));
					status.merge(checkProjectCompliance(sourceMethod));
				}

				pm.ifPresent(m -> m.worked(1));
			}

			return status;
		} finally {
			pm.ifPresent(IProgressMonitor::done);
		}
	}

	private RefactoringStatus checkGenericDeclaringType(IMethod sourceMethod, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		final RefactoringStatus status = new RefactoringStatus();
		try {
			final IMember[] pullables = new IMember[] { sourceMethod };
			monitor.ifPresent(m -> m.beginTask(RefactoringCoreMessages.PullUpRefactoring_checking, pullables.length));

			final IType declaring = sourceMethod.getDeclaringType();
			final ITypeParameter[] parameters = declaring.getTypeParameters();
			if (parameters.length > 0) {
				monitor.ifPresent(m -> m.beginTask("Retrieving target method.", IProgressMonitor.UNKNOWN));
				IMethod targetMethod = getTargetMethod(sourceMethod,
						monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN)));

				final TypeVariableMaplet[] mapping = TypeVariableUtil.subTypeToInheritedType(declaring,
						targetMethod.getDeclaringType());
				IMember member = null;
				int length = 0;
				for (int index = 0; index < pullables.length; index++) {
					member = pullables[index];
					final String[] unmapped = TypeVariableUtil.getUnmappedVariables(mapping, declaring, member);
					length = unmapped.length;

					String superClassLabel = BasicElementLabels
							.getJavaElementName(targetMethod.getDeclaringType().getElementName());
					switch (length) {
					case 0:
						break;
					case 1:
						status.addEntry(RefactoringStatus.ERROR,
								String.format(PreconditionFailure.TypeVariableNotAvailable.getMessage(), unmapped[0],
										superClassLabel),
								JavaStatusContext.create(member),
								ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID,
								PreconditionFailure.TypeVariableNotAvailable.ordinal(), sourceMethod);
						addUnmigratableMethod(sourceMethod, status.getEntryWithHighestSeverity());
						break;
					case 2:
						status.addEntry(RefactoringStatus.ERROR,
								MessageFormat.format(PreconditionFailure.TypeVariable2NotAvailable.getMessage(),
										unmapped[0], unmapped[1], superClassLabel),
								JavaStatusContext.create(member),
								ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID,
								PreconditionFailure.TypeVariable2NotAvailable.ordinal(), sourceMethod);
						addUnmigratableMethod(sourceMethod, status.getEntryWithHighestSeverity());
						break;
					case 3:
						status.addEntry(RefactoringStatus.ERROR,
								MessageFormat.format(PreconditionFailure.TypeVariable3NotAvailable.getMessage(),
										unmapped[0], unmapped[1], unmapped[2], superClassLabel),
								JavaStatusContext.create(member),
								ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID,
								PreconditionFailure.TypeVariable3NotAvailable.ordinal(), sourceMethod);
						addUnmigratableMethod(sourceMethod, status.getEntryWithHighestSeverity());
						break;
					default:
						status.addEntry(RefactoringStatus.ERROR,
								MessageFormat.format(PreconditionFailure.TypeVariablesNotAvailable.getMessage(),
										superClassLabel),
								JavaStatusContext.create(member),
								ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID,
								PreconditionFailure.TypeVariablesNotAvailable.ordinal(), sourceMethod);
						addUnmigratableMethod(sourceMethod, status.getEntryWithHighestSeverity());
					}
					monitor.ifPresent(m -> m.worked(1));
					monitor.ifPresent(m -> {
						if (m.isCanceled())
							throw new OperationCanceledException();
					});
				}
			}
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
		return status;
	}

	/**
	 * Annotations between source and target methods must be consistent. Related
	 * to #45.
	 * 
	 * @param sourceMethod
	 *            The method to check annotations.
	 * @return The resulting {@link RefactoringStatus}.
	 * @throws JavaModelException
	 *             If the {@link IAnnotation}s cannot be retrieved.
	 */
	private RefactoringStatus checkAnnotations(IMethod sourceMethod) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();
		IMethod targetMethod = getTargetMethod(sourceMethod, Optional.empty());

		if (targetMethod != null && (!checkAnnotations(sourceMethod, targetMethod).isOK()
				|| !checkAnnotations(sourceMethod.getDeclaringType(), targetMethod.getDeclaringType()).isOK()))
			addErrorAndMark(status, PreconditionFailure.AnnotationMismatch, sourceMethod, targetMethod);

		return status;
	}

	private void addUnmigratableMethod(IMethod method, Object reason) {
		this.getUnmigratableMethods().add(method);
		this.logInfo(
				"Method " + getElementLabel(method, ALL_FULLY_QUALIFIED) + " is not migratable because: " + reason);
	}

	private RefactoringStatus checkAnnotations(IAnnotatable source, IAnnotatable target) throws JavaModelException {
		// a set of annotations from the source method.
		Set<IAnnotation> sourceAnnotationSet = new HashSet<>(Arrays.asList(source.getAnnotations()));

		// remove any annotations to not consider.
		removeSpecialAnnotations(sourceAnnotationSet);

		// a set of source method annotation names.
		Set<String> sourceMethodAnnotationElementNames = sourceAnnotationSet.parallelStream()
				.map(IAnnotation::getElementName).collect(Collectors.toSet());

		// a set of target method annotation names.
		Set<String> targetAnnotationElementNames = getAnnotationElementNames(target);

		// if the source method annotation names don't match the target method
		// annotation names.
		if (!sourceMethodAnnotationElementNames.equals(targetAnnotationElementNames))
			return RefactoringStatus.createErrorStatus(PreconditionFailure.AnnotationNameMismatch.getMessage(),
					new RefactoringStatusContext() {

						@Override
						public Object getCorrespondingElement() {
							return source;
						}
					});
		else { // otherwise, we have the same annotations names. Check the
				// values.
			for (IAnnotation sourceAnnotation : sourceAnnotationSet) {
				IMemberValuePair[] sourcePairs = sourceAnnotation.getMemberValuePairs();

				IAnnotation targetAnnotation = target.getAnnotation(sourceAnnotation.getElementName());
				IMemberValuePair[] targetPairs = targetAnnotation.getMemberValuePairs();

				if (sourcePairs.length != targetPairs.length) {
					// TODO: Can perhaps analyze this situation further by
					// looking up default field values.
					logWarning(
							"There may be differences in the length of the value vectors as some annotations may use default field values.");
					return new RefactoringStatus();
				}

				Arrays.parallelSort(sourcePairs, Comparator.comparing(IMemberValuePair::getMemberName));
				Arrays.parallelSort(targetPairs, Comparator.comparing(IMemberValuePair::getMemberName));

				for (int i = 0; i < sourcePairs.length; i++)
					if (!sourcePairs[i].getMemberName().equals(targetPairs[i].getMemberName())
							|| sourcePairs[i].getValueKind() != targetPairs[i].getValueKind()
							|| !(sourcePairs[i].getValue().equals(targetPairs[i].getValue())))
						return RefactoringStatus.createErrorStatus(
								formatMessage(PreconditionFailure.AnnotationValueMismatch.getMessage(),
										sourceAnnotation, targetAnnotation),
								JavaStatusContext.create(findEnclosingMember(sourceAnnotation)));
			}
		}
		return new RefactoringStatus(); // OK.
	}

	/**
	 * Remove any annotations that we don't want considered.
	 * 
	 * @param annotationSet
	 *            The set of annotations to work with.
	 */
	private void removeSpecialAnnotations(Set<IAnnotation> annotationSet) {
		// Special case: don't consider the @Override annotation in the source
		// (the target will never have this) #67.
		annotationSet.removeIf(a -> a.getElementName().equals(Override.class.getName()));
		annotationSet.removeIf(a -> a.getElementName().equals(Override.class.getSimpleName()));
	}

	private static IMember findEnclosingMember(IJavaElement element) {
		if (element == null)
			return null;
		else if (element instanceof IMember)
			return (IMember) element;
		else
			return findEnclosingMember(element.getParent());
	}

	private Set<String> getAnnotationElementNames(IAnnotatable annotatable) throws JavaModelException {
		return Arrays.stream(annotatable.getAnnotations()).parallel().map(IAnnotation::getElementName)
				.collect(Collectors.toSet());
	}

	/**
	 * #44: Ensure that exception types between the source and target methods
	 * match.
	 * 
	 * @param sourceMethod
	 *            The source method.
	 * @return The corresponding {@link RefactoringStatus}.
	 * @throws JavaModelException
	 *             If there is trouble retrieving exception types from
	 *             sourceMethod.
	 */
	private RefactoringStatus checkExceptions(IMethod sourceMethod) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();

		IMethod targetMethod = getTargetMethod(sourceMethod, Optional.empty());

		if (targetMethod != null) {
			Set<String> sourceMethodExceptionTypeSet = getExceptionTypeSet(sourceMethod);
			Set<String> targetMethodExceptionTypeSet = getExceptionTypeSet(targetMethod);

			if (!sourceMethodExceptionTypeSet.equals(targetMethodExceptionTypeSet)) {
				RefactoringStatusEntry entry = addError(status, sourceMethod, PreconditionFailure.ExceptionTypeMismatch,
						sourceMethod, targetMethod);
				addUnmigratableMethod(sourceMethod, entry);
			}
		}

		return status;
	}

	private static Set<String> getExceptionTypeSet(IMethod method) throws JavaModelException {
		return Stream.of(method.getExceptionTypes()).parallel().collect(Collectors.toSet());
	}

	/**
	 * Check that the annotations in the parameters are consistent between the
	 * source and target, as well as type arguments.
	 * 
	 * FIXME: What if the annotation type is not available in the target?
	 * 
	 * @param sourceMethod
	 *            The method to check.
	 * @param monitor
	 *            An optional {@link IProgressMonitor}.
	 * @return {@link RefactoringStatus} indicating the result of the check.
	 * @throws JavaModelException
	 */
	private RefactoringStatus checkParameters(IMethod sourceMethod, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();
		ILocalVariable[] sourceMethodParameters = sourceMethod.getParameters();

		monitor.ifPresent(m -> m.beginTask(RefactoringCoreMessages.PullUpRefactoring_checking,
				sourceMethodParameters.length * 2 + 1));
		try {
			IMethod targetMethod = getTargetMethod(sourceMethod, monitor.map(m -> new SubProgressMonitor(m, 1)));

			// for each parameter.
			for (int i = 0; i < sourceMethodParameters.length; i++) {
				ILocalVariable sourceParameter = sourceMethodParameters[i];

				// get the corresponding target parameter.
				ILocalVariable targetParameter = targetMethod.getParameters()[i];

				if (!checkAnnotations(sourceParameter, targetParameter).isOK())
					addErrorAndMark(status, PreconditionFailure.MethodContainsInconsistentParameterAnnotations,
							sourceMethod, targetMethod);

				monitor.ifPresent(m -> m.worked(1));
			}

			// check generics #160.
			IMethodBinding sourceMethodBinding = resolveMethodBinding(sourceMethod, monitor);
			IMethodBinding targetMethodBinding = resolveMethodBinding(targetMethod, monitor);

			if (targetMethodBinding != null) {
				ITypeBinding[] sourceMethodParameterTypes = sourceMethodBinding.getParameterTypes();
				ITypeBinding[] targetMethodParameterTypes = targetMethodBinding.getParameterTypes();

				for (int i = 0; i < sourceMethodParameterTypes.length; i++) {
					monitor.ifPresent(m -> m.worked(1));

					ITypeBinding[] sourceMethodParameterTypeParameters = sourceMethodParameterTypes[i]
							.getTypeArguments();
					ITypeBinding[] targetMethodParamaterTypeParameters = targetMethodParameterTypes[i]
							.getTypeArguments();

					boolean hasAssignmentIncompatibleTypeParameter = false;

					if (sourceMethodParameterTypeParameters.length != targetMethodParamaterTypeParameters.length) {
						addErrorAndMark(status, PreconditionFailure.MethodContainsIncompatibleParameterTypeParameters,
								sourceMethod, targetMethod);
						break; // no more parameter types.
					} else
						for (int j = 0; j < sourceMethodParameterTypeParameters.length; j++) {
							// if both aren't type variables.
							if (!typeArgumentsAreTypeVariables(sourceMethodParameterTypeParameters[j],
									targetMethodParamaterTypeParameters[j])) {
								// then check if they are assignment compatible.
								if (!isAssignmentCompatible(sourceMethodParameterTypeParameters[j],
										targetMethodParamaterTypeParameters[j])) {
									addErrorAndMark(status,
											PreconditionFailure.MethodContainsIncompatibleParameterTypeParameters,
											sourceMethod, targetMethod);
									hasAssignmentIncompatibleTypeParameter = true;
									break; // no more type parameters.
								}
							}
						}

					if (hasAssignmentIncompatibleTypeParameter)
						break; // no more parameter types.
				}
			}
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
		return status;
	}

	private IMethodBinding resolveMethodBinding(IMethod method, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		MethodDeclaration methodDeclarationNode = ASTNodeSearchUtil.getMethodDeclarationNode(method,
				getCompilationUnit(method.getTypeRoot(),
						new SubProgressMonitor(monitor.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN)));

		if (methodDeclarationNode != null)
			return methodDeclarationNode.resolveBinding();
		else
			return null;
	}

	/**
	 * Check that return types are compatible between the source and target
	 * methods.
	 * 
	 * @param sourceMethod
	 *            The method to check.
	 * @param monitor
	 *            Optional progress monitor.
	 * @return {@link RefactoringStatus} indicating the result of the check.
	 * @throws JavaModelException
	 */
	private RefactoringStatus checkReturnType(IMethod sourceMethod, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();

		monitor.ifPresent(m -> m.beginTask(RefactoringCoreMessages.PullUpRefactoring_checking, 4));
		try {
			IMethod targetMethod = getTargetMethod(sourceMethod, monitor.map(m -> new SubProgressMonitor(m, 1)));

			String sourceMethodReturnType = Util.getQualifiedNameFromTypeSignature(sourceMethod.getReturnType(),
					sourceMethod.getDeclaringType());
			String targetMethodReturnType = Util.getQualifiedNameFromTypeSignature(targetMethod.getReturnType(),
					targetMethod.getDeclaringType());

			if (!sourceMethodReturnType.equals(targetMethodReturnType)) {
				addErrorAndMark(status, PreconditionFailure.IncompatibleMethodReturnTypes, sourceMethod, targetMethod);
				monitor.ifPresent(m -> m.worked(3));
			} else {
				monitor.ifPresent(m -> m.worked(1));

				// check generics #160.
				ITypeBinding sourceMethodReturnTypeBinding = resolveReturnTypeBinding(sourceMethod, Optional.empty());
				ITypeBinding[] sourceMethodReturnTypeTypeArguments = sourceMethodReturnTypeBinding.getTypeArguments();

				if (sourceMethodReturnTypeTypeArguments.length > 0) {
					// type arguments exist in the return type of the source
					// method.
					ITypeBinding targetMethodReturnTypeBinding = resolveReturnTypeBinding(targetMethod,
							Optional.empty());
					ITypeBinding[] targetMethodReturnTypeTypeArguments = targetMethodReturnTypeBinding
							.getTypeArguments();

					// are the type arguments the same length?
					if (sourceMethodReturnTypeTypeArguments.length != targetMethodReturnTypeTypeArguments.length) {
						addErrorAndMark(status, PreconditionFailure.IncompatibleMethodReturnTypes, sourceMethod,
								targetMethod);
						monitor.ifPresent(m -> m.worked(2));
					} else {
						monitor.ifPresent(m -> m.worked(1));

						// are the type arguments compatible?
						for (int i = 0; i < sourceMethodReturnTypeTypeArguments.length; i++) {
							if (!typeArgumentsAreTypeVariables(sourceMethodReturnTypeTypeArguments[i],
									targetMethodReturnTypeTypeArguments[i])) {
								// then, we should check their assignment
								// compatibility.
								if (!isAssignmentCompatible(sourceMethodReturnTypeTypeArguments[i],
										targetMethodReturnTypeTypeArguments[i])) {
									addErrorAndMark(status, PreconditionFailure.IncompatibleMethodReturnTypes,
											sourceMethod, targetMethod);
									break;
								}
							}
							monitor.ifPresent(m -> m.worked(1));
						}
					}
				}
			}
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}

		return status;
	}

	private boolean typeArgumentsAreTypeVariables(ITypeBinding typeArgument, ITypeBinding otherTypeArgument) {
		return typeArgument.isTypeVariable() && otherTypeArgument.isTypeVariable();
	}

	private static boolean isAssignmentCompatible(ITypeBinding typeBinding, ITypeBinding otherTypeBinding) {
		// Workaround
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=493965.
		if (typeBinding == null && otherTypeBinding == null)
			return true;
		else if (typeBinding == null || otherTypeBinding == null)
			return false;
		else
			return typeBinding.isAssignmentCompatible(otherTypeBinding) || typeBinding.isInterface()
					&& otherTypeBinding.isInterface() && (typeBinding.isEqualTo(otherTypeBinding) || Arrays
							.stream(typeBinding.getInterfaces()).anyMatch(itb -> itb.isEqualTo(otherTypeBinding)));
	}

	private ITypeBinding resolveReturnTypeBinding(IMethod method, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		MethodDeclaration methodDeclarationNode = ASTNodeSearchUtil.getMethodDeclarationNode(method,
				getCompilationUnit(method.getTypeRoot(),
						new SubProgressMonitor(monitor.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN)));

		if (methodDeclarationNode != null) {
			Type returnType = methodDeclarationNode.getReturnType2();
			return returnType.resolveBinding();
		} else
			return null;
	}

	private RefactoringStatus checkStructure(IMember member) throws JavaModelException {
		if (!member.isStructureKnown()) {
			return RefactoringStatus.createErrorStatus(
					MessageFormat.format(Messages.CUContainsCompileErrors, getElementLabel(member, ALL_FULLY_QUALIFIED),
							getElementLabel(member.getCompilationUnit(), ALL_FULLY_QUALIFIED)),
					JavaStatusContext.create(member.getCompilationUnit()));
		}
		return new RefactoringStatus();
	}

	private static RefactoringStatusEntry getLastRefactoringStatusEntry(RefactoringStatus status) {
		return status.getEntryAt(status.getEntries().length - 1);
	}

	private RefactoringStatus checkWritabilitiy(IMember member, PreconditionFailure failure) {
		if (member.isBinary() || member.isReadOnly()) {
			return createError(failure, member);
		}
		return new RefactoringStatus();
	}

	private RefactoringStatus checkExistence(IMember member, PreconditionFailure failure) {
		if (member == null || !member.exists()) {
			return createError(failure, member);
		}
		return new RefactoringStatus();
	}

	public Set<IMethod> getSourceMethods() {
		return this.sourceMethods;
	}

	public Set<IMethod> getUnmigratableMethods() {
		return this.unmigratableMethods;
	}

	private RefactoringStatus checkSourceMethodBodies(Optional<IProgressMonitor> pm) throws JavaModelException {
		try {
			RefactoringStatus status = new RefactoringStatus();
			pm.ifPresent(m -> m.beginTask("Checking source method bodies ...", this.getSourceMethods().size()));

			Iterator<IMethod> it = this.getSourceMethods().iterator();
			while (it.hasNext()) {
				IMethod sourceMethod = it.next();
				MethodDeclaration declaration = getMethodDeclaration(sourceMethod, pm);

				if (declaration != null) {
					Block body = declaration.getBody();

					if (body != null) {
						SourceMethodBodyAnalysisVisitor visitor = new SourceMethodBodyAnalysisVisitor(sourceMethod, pm);
						body.accept(visitor);

						if (visitor.doesMethodContainsSuperReference())
							addErrorAndMark(status, PreconditionFailure.MethodContainsSuperReference, sourceMethod);

						if (visitor.doesMethodContainsCallToProtectedObjectMethod())
							addErrorAndMark(status, PreconditionFailure.MethodContainsCallToProtectedObjectMethod,
									sourceMethod,
									visitor.getCalledProtectedObjectMethodSet().stream().findAny().orElseThrow(
											() -> new IllegalStateException("No associated object method")));

						if (visitor.doesMethodContainsTypeIncompatibleThisReference()) {
							// FIXME: The error context should be the this
							// reference that caused the error.
							addErrorAndMark(status, PreconditionFailure.MethodContainsTypeIncompatibleThisReference,
									sourceMethod);
						}

						if (sourceMethod.getDeclaringType().isMember()
								&& visitor.doesMethodContainQualifiedThisExpression()) {
							// FIXME: The error context should be the this
							// reference that caused the error.
							addErrorAndMark(status, PreconditionFailure.MethodContainsQualifiedThisExpression,
									sourceMethod);
						}
					}
				}
				pm.ifPresent(m -> m.worked(1));
			}
			return status;
		} finally {
			pm.ifPresent(IProgressMonitor::done);
		}
	}

	private MethodDeclaration getMethodDeclaration(IMethod method, Optional<IProgressMonitor> pm)
			throws JavaModelException {
		ITypeRoot root = method.getCompilationUnit();
		CompilationUnit unit = this.getCompilationUnit(root,
				new SubProgressMonitor(pm.orElseGet(NullProgressMonitor::new), IProgressMonitor.UNKNOWN));
		MethodDeclaration declaration = ASTNodeSearchUtil.getMethodDeclarationNode(method, unit);
		return declaration;
	}

	private static void addWarning(RefactoringStatus status, IMethod sourceMethod, PreconditionFailure failure,
			IJavaElement... relatedElementCollection) {
		addEntry(status, sourceMethod, RefactoringStatus.WARNING, failure, relatedElementCollection);
	}

	private static RefactoringStatusEntry addError(RefactoringStatus status, IMethod sourceMethod,
			PreconditionFailure failure, IJavaElement... relatedElementCollection) {
		addEntry(status, sourceMethod, RefactoringStatus.ERROR, failure, relatedElementCollection);
		return getLastRefactoringStatusEntry(status);
	}

	private static void addEntry(RefactoringStatus status, IMethod sourceMethod, int severity,
			PreconditionFailure failure, IJavaElement... relatedElementCollection) {
		String message = formatMessage(failure.getMessage(), relatedElementCollection);

		// add the first element as the context if appropriate.
		if (relatedElementCollection.length > 0 && relatedElementCollection[0] instanceof IMember) {
			IMember member = (IMember) relatedElementCollection[0];
			RefactoringStatusContext context = JavaStatusContext.create(member);
			status.addEntry(new RefactoringStatusEntry(severity, message, context,
					ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID, failure.ordinal(),
					sourceMethod));
		} else // otherwise, just add the message.
			status.addEntry(new RefactoringStatusEntry(severity, message, null,
					ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID, failure.ordinal(),
					sourceMethod));
	}

	private static String formatMessage(String message, IJavaElement... relatedElementCollection) {
		Object[] elementNames = Arrays.stream(relatedElementCollection).parallel().filter(Objects::nonNull)
				.map(re -> getElementLabel(re, ALL_FULLY_QUALIFIED)).toArray();
		message = MessageFormat.format(message, elementNames);
		return message;
	}

	private static RefactoringStatusEntry addError(RefactoringStatus status, IMethod sourceMethod,
			PreconditionFailure failure, IMember member, IMember... more) {
		List<String> elementNames = new ArrayList<>();
		elementNames.add(getElementLabel(member, ALL_FULLY_QUALIFIED));

		Stream<String> stream = Arrays.asList(more).parallelStream().map(m -> getElementLabel(m, ALL_FULLY_QUALIFIED));
		Stream<String> concat = Stream.concat(elementNames.stream(), stream);
		List<String> collect = concat.collect(Collectors.toList());

		status.addEntry(RefactoringStatus.ERROR, MessageFormat.format(failure.getMessage(), collect.toArray()),
				JavaStatusContext.create(member),
				ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID, failure.ordinal(),
				sourceMethod);
		return getLastRefactoringStatusEntry(status);
	}

	private static RefactoringStatus createWarning(String message, IMember member) {
		return createRefactoringStatus(message, member, RefactoringStatus::createWarningStatus);
	}

	private RefactoringStatus createError(PreconditionFailure failure, IMember member) {
		return createRefactoringStatus(failure.getMessage(), member, RefactoringStatus::createErrorStatus);
	}

	private static RefactoringStatus createFatalError(String message, IMember member) {
		return createRefactoringStatus(message, member, RefactoringStatus::createFatalErrorStatus);
	}

	private static RefactoringStatus createRefactoringStatus(String message, IMember member,
			BiFunction<String, RefactoringStatusContext, RefactoringStatus> function) {
		String elementName = getElementLabel(member, ALL_FULLY_QUALIFIED);
		return function.apply(MessageFormat.format(message, elementName), JavaStatusContext.create(member));
	}

	/**
	 * Creates a working copy layer if necessary.
	 *
	 * @param monitor
	 *            the progress monitor to use
	 * @return a status describing the outcome of the operation
	 */
	private RefactoringStatus createWorkingCopyLayer(IProgressMonitor monitor) {
		try {
			monitor.beginTask(Messages.CheckingPreconditions, 1);
			// TODO ICompilationUnit unit =
			// getDeclaringType().getCompilationUnit();
			// if (fLayer)
			// unit = unit.findWorkingCopy(fOwner);
			// resetWorkingCopies(unit);
			return new RefactoringStatus();
		} finally {
			monitor.done();
		}
	}

	@Override
	public RefactoringStatus checkFinalConditions(final IProgressMonitor monitor, final CheckConditionsContext context)
			throws CoreException, OperationCanceledException {
		try {
			monitor.beginTask(Messages.CheckingPreconditions, 12);

			final RefactoringStatus status = new RefactoringStatus();

			// workaround https://bugs.eclipse.org/bugs/show_bug.cgi?id=474524.
			if (!this.getSourceMethods().isEmpty())
				status.merge(createWorkingCopyLayer(new SubProgressMonitor(monitor, 4)));
			if (status.hasFatalError())
				return status;
			if (monitor.isCanceled())
				throw new OperationCanceledException();

			status.merge(checkSourceMethods(Optional.of(new SubProgressMonitor(monitor, 1))));
			if (status.hasFatalError())
				return status;
			if (monitor.isCanceled())
				throw new OperationCanceledException();

			status.merge(checkSourceMethodBodies(Optional.of(new SubProgressMonitor(monitor, 1))));
			if (status.hasFatalError())
				return status;
			if (monitor.isCanceled())
				throw new OperationCanceledException();

			status.merge(checkDestinationInterfaces(Optional.of(new SubProgressMonitor(monitor, 1))));
			if (status.hasFatalError())
				return status;
			if (monitor.isCanceled())
				throw new OperationCanceledException();

			status.merge(checkTargetMethods(Optional.of(new SubProgressMonitor(monitor, 1))));

			// check if there are any methods left to migrate.
			if (this.getUnmigratableMethods().containsAll(this.getSourceMethods()))
				// if not, we have a fatal error.
				status.addFatalError(Messages.NoStreamsHavePassedThePreconditions);

			// TODO:
			// Checks.addModifiedFilesToChecker(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits()),
			// context);

			return status;
		} catch (Exception e) {
			JavaPlugin.log(e);
			throw e;
		} finally {
			monitor.done();
		}
	}

	private RefactoringStatus checkTargetMethods(Optional<IProgressMonitor> monitor) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();

		// first, create a map of target methods to their set of migratable
		// source methods.
		Map<IMethod, Set<IMethod>> targetMethodToMigratableSourceMethodsMap = createTargetMethodToMigratableSourceMethodsMap(
				monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN)));

		monitor.ifPresent(m -> m.beginTask("Checking target methods ...",
				targetMethodToMigratableSourceMethodsMap.keySet().size()));

		// for each target method.
		for (IMethod targetMethod : targetMethodToMigratableSourceMethodsMap.keySet()) {
			Set<IMethod> migratableSourceMethods = targetMethodToMigratableSourceMethodsMap.get(targetMethod);

			// if the target method is associated with multiple source methods.
			if (migratableSourceMethods.size() > 1) {
				// we need to decide which of the source methods will be
				// migrated and which will not. We'll build equivalence sets to
				// see which of the source method bodies are the same. Then,
				// we'll pick the largest equivalence set to migrate. That will
				// reduce the greatest number of methods in the system. The
				// other sets will become unmigratable. Those methods will just
				// override the new default method.

				// build the equivalence sets using a union–find data structure
				// (MakeSet).
				Set<Set<IMethod>> equivalenceSets = createEquivalenceSets(migratableSourceMethods);

				// merge the sets.
				mergeEquivalenceSets(equivalenceSets, monitor);

				// find the largest set size.
				equivalenceSets.stream().map(s -> s.size()).max(Integer::compareTo)
						// find the first set with this size.
						.flatMap(size -> equivalenceSets.stream().filter(s -> s.size() == size).findFirst()).ifPresent(
								// for all of the methods in the other sets ...
								fls -> equivalenceSets.stream().filter(s -> s != fls).flatMap(s -> s.stream())
										// mark them as unmigratable.
										.forEach(m -> addErrorAndMark(status,
												PreconditionFailure.TargetMethodHasMultipleSourceMethods, m,
												targetMethod)));
			}
			monitor.ifPresent(m -> m.worked(1));
		}

		monitor.ifPresent(IProgressMonitor::done);
		return status;
	}

	private void mergeEquivalenceSets(Set<Set<IMethod>> equivalenceSets, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		// A map of methods to their equivalence set.
		Map<IMethod, Set<IMethod>> methodToEquivalenceSetMap = new LinkedHashMap<>();
		for (Set<IMethod> set : equivalenceSets) {
			for (IMethod method : set) {
				methodToEquivalenceSetMap.put(method, set);
			}
		}

		monitor.ifPresent(
				m -> m.beginTask("Merging method equivalence sets ...", methodToEquivalenceSetMap.keySet().size()));

		for (IMethod method : methodToEquivalenceSetMap.keySet()) {
			for (IMethod otherMethod : methodToEquivalenceSetMap.keySet()) {
				if (method != otherMethod) {
					Set<IMethod> methodSet = methodToEquivalenceSetMap.get(method); // Find(method)
					Set<IMethod> otherMethodSet = methodToEquivalenceSetMap.get(otherMethod); // Find(otherMethod)

					// if they are different sets and the elements are
					// equivalent.
					if (methodSet != otherMethodSet && isEquivalent(method, otherMethod,
							monitor.map(m -> new SubProgressMonitor(m, IProgressMonitor.UNKNOWN)))) {
						// Union(Find(method), Find(otherMethod))
						methodSet.addAll(otherMethodSet);
						equivalenceSets.remove(otherMethodSet);

						// update the map.
						for (IMethod methodInOtherMethodSet : otherMethodSet) {
							methodToEquivalenceSetMap.put(methodInOtherMethodSet, methodSet);
						}
					}
				}
			}
			monitor.ifPresent(m -> m.worked(1));
		}

		monitor.ifPresent(IProgressMonitor::done);
	}

	private boolean isEquivalent(IMethod method, IMethod otherMethod, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		monitor.ifPresent(m -> m.beginTask("Checking method equivalence ...", 2));

		MethodDeclaration methodDeclaration = this.getMethodDeclaration(method,
				monitor.map(m -> new SubProgressMonitor(m, 1)));

		MethodDeclaration otherMethodDeclaration = this.getMethodDeclaration(otherMethod,
				monitor.map(m -> new SubProgressMonitor(m, 1)));

		monitor.ifPresent(IProgressMonitor::done);

		Block methodDeclarationBody = methodDeclaration.getBody();
		Block otherMethodDeclarationBody = otherMethodDeclaration.getBody();

		boolean match = methodDeclarationBody.subtreeMatch(new ASTMatcher(), otherMethodDeclarationBody);
		return match;
	}

	private static Set<Set<IMethod>> createEquivalenceSets(Set<IMethod> migratableSourceMethods) {
		Set<Set<IMethod>> ret = new LinkedHashSet<>();

		migratableSourceMethods.stream().forEach(m -> {
			Set<IMethod> set = new LinkedHashSet<>();
			set.add(m);
			ret.add(set);
		});

		return ret;
	}

	private Map<IMethod, Set<IMethod>> createTargetMethodToMigratableSourceMethodsMap(
			Optional<IProgressMonitor> monitor) throws JavaModelException {
		Map<IMethod, Set<IMethod>> ret = new LinkedHashMap<>();
		Set<IMethod> migratableMethods = this.getMigratableMethods();

		monitor.ifPresent(m -> m.beginTask("Finding migratable source methods for each target method ...",
				migratableMethods.size()));

		for (IMethod sourceMethod : migratableMethods) {
			IMethod targetMethod = getTargetMethod(sourceMethod, Optional.empty());

			ret.compute(targetMethod, (k, v) -> {
				if (v == null) {
					Set<IMethod> sourceMethodSet = new LinkedHashSet<>();
					sourceMethodSet.add(sourceMethod);
					return sourceMethodSet;
				} else {
					v.add(sourceMethod);
					return v;
				}
			});
			monitor.ifPresent(m -> m.worked(1));
		}

		monitor.ifPresent(IProgressMonitor::done);
		return ret;
	}

	private void clearCaches() {
		getTypeToSuperTypeHierarchyMap().clear();
		getMethodToTargetMethodMap().clear();
		getTypeToTypeHierarchyMap().clear();
		getCompilationUnitToCompilationUnitRewriteMap().clear();
	}

	public TimeCollector getExcludedTimeCollector() {
		return excludedTimeCollector;
	}

	private RefactoringStatus checkProjectCompliance(IMethod sourceMethod) throws JavaModelException {
		RefactoringStatus status = new RefactoringStatus();
		IMethod targetMethod = getTargetMethod(sourceMethod, Optional.empty());
		IJavaProject destinationProject = targetMethod.getJavaProject();

		if (!JavaModelUtil.is18OrHigher(destinationProject))
			addErrorAndMark(status, PreconditionFailure.DestinationProjectIncompatible, sourceMethod, targetMethod);

		return status;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		try {
			pm.beginTask(Messages.CreatingChange, 1);

			final TextEditBasedChangeManager manager = new TextEditBasedChangeManager();

			Set<IMethod> migratableMethods = this.getMigratableMethods();

			if (migratableMethods.isEmpty())
				return new NullChange(Messages.NoStreamsToConvert);

			// the set of target methods that we transformed to default methods.
			Set<IMethod> transformedTargetMethods = new HashSet<>(migratableMethods.size());

			for (IMethod sourceMethod : migratableMethods) {
				// get the source method declaration.
				CompilationUnit sourceCompilationUnit = getCompilationUnit(sourceMethod.getTypeRoot(), pm);
				MethodDeclaration sourceMethodDeclaration = ASTNodeSearchUtil.getMethodDeclarationNode(sourceMethod,
						sourceCompilationUnit);
				logInfo("Source method declaration: " + sourceMethodDeclaration);

				CompilationUnitRewrite sourceRewrite = getCompilationUnitRewrite(sourceMethod.getCompilationUnit(),
						sourceCompilationUnit);

				// Find the target method.
				IMethod targetMethod = getTargetMethod(sourceMethod,
						Optional.of(new SubProgressMonitor(pm, IProgressMonitor.UNKNOWN)));

				// if we have not already transformed this method
				if (!transformedTargetMethods.contains(targetMethod)) {
					IType destinationInterface = targetMethod.getDeclaringType();

					logInfo("Migrating method: " + getElementLabel(sourceMethod, ALL_FULLY_QUALIFIED)
							+ " to interface: " + destinationInterface.getFullyQualifiedName());

					CompilationUnit destinationCompilationUnit = this
							.getCompilationUnit(destinationInterface.getTypeRoot(), pm);
					CompilationUnitRewrite destinationCompilationUnitRewrite = getCompilationUnitRewrite(
							targetMethod.getCompilationUnit(), destinationCompilationUnit);
					ImportRewriteContext context = new ContextSensitiveImportRewriteContext(destinationCompilationUnit,
							destinationCompilationUnitRewrite.getImportRewrite());

					MethodDeclaration targetMethodDeclaration = ASTNodeSearchUtil.getMethodDeclarationNode(targetMethod,
							destinationCompilationUnit);

					final TypeVariableMaplet[] mapping = TypeVariableUtil.subTypeToSuperType(
							sourceMethod.getDeclaringType(), targetMethod.getDeclaringType(),
							targetMethod.getDeclaringType());

					// tack on the source method body to the target method.
					pm.beginTask("Copying source method body ...", IProgressMonitor.UNKNOWN);
					copyMethodBody(sourceRewrite, destinationCompilationUnitRewrite, sourceMethod,
							sourceMethodDeclaration, targetMethodDeclaration, mapping,
							new SubProgressMonitor(pm, IProgressMonitor.UNKNOWN));

					// add any static imports needed to the target method's
					// compilation unit for static fields referenced in the
					// source method.
					addStaticImports(sourceMethodDeclaration, targetMethodDeclaration,
							destinationCompilationUnitRewrite, context);

					// alter the target parameter names to match that of the
					// source method if necessary #148.
					changeTargetMethodParametersToMatchSource(sourceMethodDeclaration, targetMethodDeclaration,
							destinationCompilationUnitRewrite.getASTRewrite());

					// Change the target method to default.
					convertToDefault(targetMethodDeclaration, destinationCompilationUnitRewrite.getASTRewrite());

					// Remove any abstract modifiers from the target method as
					// both abstract and default are not allowed.
					removeAbstractness(targetMethodDeclaration, destinationCompilationUnitRewrite.getASTRewrite());

					// TODO: Do we need to worry about preserving ordering of
					// the
					// modifiers?

					// if the source method is strictfp.
					// FIXME: Actually, I think we need to check that, in the
					// case the target method isn't already strictfp, that the
					// other
					// methods in the hierarchy are.
					if ((Flags.isStrictfp(sourceMethod.getFlags())
							|| Flags.isStrictfp(sourceMethod.getDeclaringType().getFlags()))
							&& !Flags.isStrictfp(targetMethod.getFlags()))
						// change the target method to strictfp.
						convertToStrictFP(targetMethodDeclaration, destinationCompilationUnitRewrite.getASTRewrite());

					// deal with imports #22.
					ImportRewriteUtil.addImports(destinationCompilationUnitRewrite, context, sourceMethodDeclaration,
							new HashMap<Name, String>(), new HashMap<Name, String>(), false);

					transformedTargetMethods.add(targetMethod);
				}

				// Remove the source method.
				removeMethod(sourceMethodDeclaration, sourceRewrite.getASTRewrite());
				sourceRewrite.getImportRemover().registerRemovedNode(sourceMethodDeclaration);
			}

			// save the source changes.
			ICompilationUnit[] units = this.getCompilationUnitToCompilationUnitRewriteMap().keySet().stream()
					.filter(cu -> !manager.containsChangesIn(cu)).toArray(ICompilationUnit[]::new);

			for (ICompilationUnit cu : units) {
				CompilationUnit compilationUnit = getCompilationUnit(cu, pm);
				manageCompilationUnit(manager, getCompilationUnitRewrite(cu, compilationUnit),
						Optional.of(new SubProgressMonitor(pm, IProgressMonitor.UNKNOWN)));
			}

			final Map<String, String> arguments = new HashMap<>();
			int flags = RefactoringDescriptor.STRUCTURAL_CHANGE | RefactoringDescriptor.MULTI_CHANGE;

			// TODO: Fill in description.

			ConvertStreamToParallelRefactoringDescriptor descriptor = new ConvertStreamToParallelRefactoringDescriptor(
					null, "TODO", null, arguments, flags);

			return new DynamicValidationRefactoringChange(descriptor, getProcessorName(), manager.getAllChanges());
		} finally {
			pm.done();
			this.clearCaches();
		}
	}

	/**
	 * Add any static imports needed to the target method's compilation unit for
	 * static fields referenced in the source method.
	 * 
	 * @param sourceMethodDeclaration
	 *            The method being migrated.
	 * @param targetMethodDeclaration
	 *            The target for the migration.
	 * @param destinationCompilationUnitRewrite
	 *            The rewrite associated with the target's compilation unit.
	 * @param context
	 *            The context in which to add static imports if necessary.
	 */
	private void addStaticImports(MethodDeclaration sourceMethodDeclaration, MethodDeclaration targetMethodDeclaration,
			CompilationUnitRewrite destinationCompilationUnitRewrite, ImportRewriteContext context) {
		sourceMethodDeclaration.accept(new ASTVisitor() {

			@Override
			public boolean visit(SimpleName node) {
				// add any necessary static imports.
				if (node.getParent().getNodeType() != ASTNode.QUALIFIED_NAME) {
					IBinding binding = node.resolveBinding();
					if (binding.getKind() == IBinding.VARIABLE) {
						IVariableBinding variableBinding = ((IVariableBinding) binding);
						if (variableBinding.isField()) {
							ITypeBinding declaringClass = variableBinding.getDeclaringClass();
							processStaticImport(variableBinding, declaringClass, targetMethodDeclaration,
									destinationCompilationUnitRewrite, context);
						}
					}
				}
				return super.visit(node);
			}

			private void processStaticImport(IBinding binding, ITypeBinding declaringClass,
					MethodDeclaration targetMethodDeclaration, CompilationUnitRewrite destinationCompilationUnitRewrite,
					ImportRewriteContext context) {
				if (Modifier.isStatic(binding.getModifiers())
						&& !declaringClass.isEqualTo(targetMethodDeclaration.resolveBinding().getDeclaringClass()))
					destinationCompilationUnitRewrite.getImportRewrite().addStaticImport(binding, context);
			}

			@Override
			public boolean visit(MethodInvocation node) {
				if (node.getExpression() == null) { // it's an
													// unqualified
													// method call.
					IMethodBinding methodBinding = node.resolveMethodBinding();
					ITypeBinding declaringClass = methodBinding.getDeclaringClass();
					processStaticImport(methodBinding, declaringClass, targetMethodDeclaration,
							destinationCompilationUnitRewrite, context);
				}
				return super.visit(node);
			}
		});
	}

	private CompilationUnit getCompilationUnit(ITypeRoot root, IProgressMonitor pm) {
		CompilationUnit compilationUnit = this.typeRootToCompilationUnitMap.get(root);
		if (compilationUnit == null) {
			this.getExcludedTimeCollector().start();
			compilationUnit = RefactoringASTParser.parseWithASTProvider(root, true, pm);
			this.getExcludedTimeCollector().stop();
			this.typeRootToCompilationUnitMap.put(root, compilationUnit);
		}
		return compilationUnit;
	}

	private CompilationUnitRewrite getCompilationUnitRewrite(ICompilationUnit unit, CompilationUnit root) {
		CompilationUnitRewrite rewrite = this.getCompilationUnitToCompilationUnitRewriteMap().get(unit);
		if (rewrite == null) {
			rewrite = new CompilationUnitRewrite(unit, root);
			this.getCompilationUnitToCompilationUnitRewriteMap().put(unit, rewrite);
		}
		return rewrite;
	}

	private void manageCompilationUnit(final TextEditBasedChangeManager manager, CompilationUnitRewrite rewrite,
			Optional<IProgressMonitor> monitor) throws CoreException {
		monitor.ifPresent(m -> m.beginTask("Creating change ...", IProgressMonitor.UNKNOWN));
		CompilationUnitChange change = rewrite.createChange(false, monitor.orElseGet(NullProgressMonitor::new));

		if (change != null)
			change.setTextType("java");

		manager.manage(rewrite.getCu(), change);
	}

	private void changeTargetMethodParametersToMatchSource(MethodDeclaration sourceMethodDeclaration,
			MethodDeclaration targetMethodDeclaration, ASTRewrite destinationRewrite) {
		Assert.isLegal(sourceMethodDeclaration.parameters().size() == targetMethodDeclaration.parameters().size());

		// iterate over the source method parameters.
		for (int i = 0; i < sourceMethodDeclaration.parameters().size(); i++) {
			// get the parameter for the source method.
			SingleVariableDeclaration sourceParameter = (SingleVariableDeclaration) sourceMethodDeclaration.parameters()
					.get(i);
			// get the corresponding target method parameter.
			SingleVariableDeclaration targetParameter = (SingleVariableDeclaration) targetMethodDeclaration.parameters()
					.get(i);

			// if the names don't match.
			if (!sourceParameter.getName().equals(targetParameter.getName())) {
				// change the target method parameter to match it since that is
				// what the body will use.
				ASTNode sourceParameterNameCopy = ASTNode.copySubtree(destinationRewrite.getAST(),
						sourceParameter.getName());
				destinationRewrite.replace(targetParameter.getName(), sourceParameterNameCopy, null);
			}
		}
	}

	private void copyMethodBody(final CompilationUnitRewrite sourceRewrite, final CompilationUnitRewrite targetRewrite,
			final IMethod method, final MethodDeclaration oldMethod, final MethodDeclaration newMethod,
			final TypeVariableMaplet[] mapping, final IProgressMonitor monitor) throws JavaModelException {
		final Block body = oldMethod.getBody();
		if (body == null) {
			newMethod.setBody(null);
			return;
		}
		try {
			final IDocument document = new Document(method.getCompilationUnit().getBuffer().getContents());
			final ASTRewrite rewrite = ASTRewrite.create(body.getAST());
			final ITrackedNodePosition position = rewrite.track(body);
			body.accept(new TypeVariableMapper(rewrite, mapping));
			rewrite.rewriteAST(document, method.getJavaProject().getOptions(true)).apply(document, TextEdit.NONE);
			String content = document.get(position.getStartPosition(), position.getLength());
			final String[] lines = Strings.convertIntoLines(content);
			Strings.trimIndentation(lines, method.getJavaProject(), false);
			content = Strings.concatenate(lines, StubUtility.getLineDelimiterUsed(method));
			ASTNode stringPlaceholder = targetRewrite.getASTRewrite().createStringPlaceholder(content, ASTNode.BLOCK);
			targetRewrite.getASTRewrite().set(newMethod, MethodDeclaration.BODY_PROPERTY, stringPlaceholder, null);
		} catch (MalformedTreeException exception) {
			JavaPlugin.log(exception);
		} catch (BadLocationException exception) {
			JavaPlugin.log(exception);
		}
	}

	private void removeMethod(MethodDeclaration methodDeclaration, ASTRewrite rewrite) {
		// TODO: Do we need an edit group??
		rewrite.remove(methodDeclaration, null);
	}

	private void convertToDefault(MethodDeclaration methodDeclaration, ASTRewrite rewrite) {
		addModifierKeyword(methodDeclaration, ModifierKeyword.DEFAULT_KEYWORD, rewrite);
	}

	private void removeAbstractness(MethodDeclaration methodDeclaration, ASTRewrite rewrite) {
		removeModifierKeyword(methodDeclaration, ModifierKeyword.ABSTRACT_KEYWORD, rewrite);
	}

	private void convertToStrictFP(MethodDeclaration methodDeclaration, ASTRewrite rewrite) {
		addModifierKeyword(methodDeclaration, ModifierKeyword.STRICTFP_KEYWORD, rewrite);
	}

	private void addModifierKeyword(MethodDeclaration methodDeclaration, ModifierKeyword modifierKeyword,
			ASTRewrite rewrite) {
		Modifier modifier = rewrite.getAST().newModifier(modifierKeyword);
		ListRewrite listRewrite = rewrite.getListRewrite(methodDeclaration, methodDeclaration.getModifiersProperty());
		listRewrite.insertLast(modifier, null);
	}

	@SuppressWarnings("unchecked")
	private void removeModifierKeyword(MethodDeclaration methodDeclaration, ModifierKeyword modifierKeyword,
			ASTRewrite rewrite) {
		ListRewrite listRewrite = rewrite.getListRewrite(methodDeclaration, methodDeclaration.getModifiersProperty());
		listRewrite.getOriginalList().stream().filter(o -> o instanceof Modifier).map(Modifier.class::cast)
				.filter(m -> ((Modifier) m).getKeyword().equals(modifierKeyword)).findAny()
				.ifPresent(m -> listRewrite.remove((ASTNode) m, null));
	}

	private static Map<IMethod, IMethod> getMethodToTargetMethodMap() {
		return methodToTargetMethodMap;
	}

	/**
	 * Finds the target (interface) method declaration in the destination
	 * interface for the given source method.
	 * 
	 * TODO: Something is very wrong here. There can be multiple targets for a
	 * given source method because it can be declared in multiple interfaces up
	 * and down the hierarchy. What this method right now is really doing is
	 * finding the target method for the given source method in the destination
	 * interface. As such, we should be sure what the destination is prior to
	 * this call.
	 * 
	 * @param sourceMethod
	 *            The method that will be migrated to the target interface.
	 * @return The target method that will be manipulated or null if not found.
	 * @throws JavaModelException
	 */
	public static IMethod getTargetMethod(IMethod sourceMethod, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		IMethod targetMethod = getMethodToTargetMethodMap().get(sourceMethod);

		if (targetMethod == null) {
			IType destinationInterface = getDestinationInterface(sourceMethod, monitor);

			if (getMethodTargetInterfaceTargetMethodTable().contains(sourceMethod, destinationInterface))
				targetMethod = getMethodTargetInterfaceTargetMethodTable().get(sourceMethod, destinationInterface);
			else if (destinationInterface != null) {
				targetMethod = findTargetMethod(sourceMethod, destinationInterface);

				if (targetMethod == null)
					logWarning("Could not retrieve target method for source method: " + sourceMethod
							+ " and destination interface: " + destinationInterface);
				else
					getMethodTargetInterfaceTargetMethodTable().put(sourceMethod, destinationInterface, targetMethod);
			}

			getMethodToTargetMethodMap().put(sourceMethod, targetMethod);
		}
		return targetMethod;
	}

	private static IType getDestinationInterface(IMethod sourceMethod, Optional<IProgressMonitor> monitor)
			throws JavaModelException {
		try {
			IType[] candidateDestinationInterfaces = getCandidateDestinationInterfaces(sourceMethod,
					monitor.map(m -> new SubProgressMonitor(m, 1)));

			// FIXME: Really just returning the first match here #23.
			return Arrays.stream(candidateDestinationInterfaces).findFirst().orElse(null);
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	/**
	 * Finds the target (interface) method declaration in the given type for the
	 * given source method.
	 * 
	 * @param sourceMethod
	 *            The method that will be migrated to the target interface.
	 * @param destinationInterface
	 *            The interface for which sourceMethod will be migrated.
	 * @return The target method that will be manipulated or null if not found.
	 * @throws JavaModelException
	 */
	private static IMethod findTargetMethod(IMethod sourceMethod, IType destinationInterface)
			throws JavaModelException {
		if (destinationInterface == null)
			return null; // not found.

		Assert.isNotNull(sourceMethod);
		Assert.isLegal(sourceMethod.exists(), "Source method does not exist.");
		Assert.isLegal(destinationInterface.exists(), "Target interface does not exist.");
		Assert.isLegal(destinationInterface.isInterface(), "Target interface must be an interface.");

		IMethod ret = null;

		for (IMethod method : destinationInterface.getMethods()) {
			if (method.exists() && method.getElementName().equals(sourceMethod.getElementName())) {
				ILocalVariable[] parameters = method.getParameters();
				ILocalVariable[] sourceParameters = sourceMethod.getParameters();

				if (parameterListMatches(parameters, method, sourceParameters, sourceMethod)) {
					if (ret != null)
						throw new IllegalStateException(
								"Found multiple matches of method: " + sourceMethod.getElementName() + " in interface: "
										+ destinationInterface.getElementName());
					else
						ret = method;
				}
			}
		}
		return ret;
	}

	private static boolean parameterListMatches(ILocalVariable[] parameters, IMethod method,
			ILocalVariable[] sourceParameters, IMethod sourceMethod) throws JavaModelException {
		if (parameters.length == sourceParameters.length) {
			for (int i = 0; i < parameters.length; i++) {
				String paramString = Util.getQualifiedNameFromTypeSignature(parameters[i].getTypeSignature(),
						method.getDeclaringType());
				String sourceParamString = Util.getQualifiedNameFromTypeSignature(
						sourceParameters[i].getTypeSignature(), sourceMethod.getDeclaringType());

				if (!paramString.equals(sourceParamString))
					return false;
			}
			return true;
		} else
			return false;
	}

	private static void log(int severity, String message) {
		if (severity >= getLoggingLevel()) {
			String name = FrameworkUtil.getBundle(ConvertToParallelStreamRefactoringProcessor.class)
					.getSymbolicName();
			IStatus status = new Status(severity, name, message);
			JavaPlugin.log(status);
		}
	}

	private void logInfo(String message) {
		log(IStatus.INFO, message);
	}

	private static void logWarning(String message) {
		log(IStatus.WARNING, message);
	}

	@Override
	public String getIdentifier() {
		return ConvertStreamToParallelRefactoringDescriptor.REFACTORING_ID;
	}

	@Override
	public String getProcessorName() {
		return Messages.Name;
	}

	@Override
	public boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isInterfaceMigrationAvailable(getSourceMethods().parallelStream()
				.filter(m -> !this.getUnmigratableMethods().contains(m)).toArray(IMethod[]::new), Optional.empty());
	}

	/**
	 * Returns true if the given type is a pure interface, i.e., it is an
	 * interface but not an annotation.
	 * 
	 * @param type
	 *            The type to check.
	 * @return True if the given type is a pure interface and false otherwise.
	 * @throws JavaModelException
	 */
	private static boolean isPureInterface(IType type) throws JavaModelException {
		return type != null && type.isInterface() && !type.isAnnotation();
	}

	private Map<IType, ITypeHierarchy> typeToTypeHierarchyMap = new HashMap<>();

	private boolean setDeprecateEmptyDeclaringTypes;

	private boolean setConsiderNonstandardAnnotationDifferences;

	private Map<IType, ITypeHierarchy> getTypeToTypeHierarchyMap() {
		return typeToTypeHierarchyMap;
	}

	private ITypeHierarchy getTypeHierarchy(IType type, Optional<IProgressMonitor> monitor) throws JavaModelException {
		try {
			ITypeHierarchy ret = this.getTypeToTypeHierarchyMap().get(type);

			if (ret == null) {
				ret = type.newTypeHierarchy(monitor.orElseGet(NullProgressMonitor::new));
				this.getTypeToTypeHierarchyMap().put(type, ret);
			}

			return ret;
		} finally {
			monitor.ifPresent(IProgressMonitor::done);
		}
	}

	@Override
	public RefactoringParticipant[] loadParticipants(RefactoringStatus status, SharableParticipants sharedParticipants)
			throws CoreException {
		return new RefactoringParticipant[0];
	}

	private static Table<IMethod, IType, IMethod> getMethodTargetInterfaceTargetMethodTable() {
		return methodTargetInterfaceTargetMethodTable;
	}

	private SearchEngine getSearchEngine() {
		return searchEngine;
	}

	/**
	 * Minimum logging level. One of the constants in
	 * org.eclipse.core.runtime.IStatus.
	 * 
	 * @param level
	 *            The minimum logging level to set.
	 * @see org.eclipse.core.runtime.IStatus.
	 */
	public static void setLoggingLevel(int level) {
		loggingLevel = level;
	}

	protected static int getLoggingLevel() {
		return loggingLevel;
	}

	protected Map<ICompilationUnit, CompilationUnitRewrite> getCompilationUnitToCompilationUnitRewriteMap() {
		return this.compilationUnitToCompilationUnitRewriteMap;
	}

	public void setDeprecateEmptyDeclaringTypes(boolean deprecateDeclaringTypesValue) {
		this.setDeprecateEmptyDeclaringTypes = deprecateDeclaringTypesValue;
	}

	public void setConsiderNonstandardAnnotationDifferences(boolean noAnnotationsValue) {
		this.setConsiderNonstandardAnnotationDifferences = noAnnotationsValue;
	}
}