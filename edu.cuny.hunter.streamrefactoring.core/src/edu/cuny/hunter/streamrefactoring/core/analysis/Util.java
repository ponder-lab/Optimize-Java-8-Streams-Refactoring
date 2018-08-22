package edu.cuny.hunter.streamrefactoring.core.analysis;

import static edu.cuny.hunter.streamrefactoring.core.wala.AnalysisUtils.isJDKClass;
import static edu.cuny.hunter.streamrefactoring.core.wala.AnalysisUtils.isLibraryClass;

import java.io.UTFDataFormatException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.BaseStream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;

import com.ibm.wala.analysis.typeInference.PointType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.JDTIdentityMapper;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IMethod.SourcePosition;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.FakeRootMethod;
import com.ibm.wala.ipa.callgraph.impl.FakeWorldClinitMethod;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.NormalAllocationInNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContext;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContextSelector;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.PhiValue;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.Value;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.StringStuff;

import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;
import edu.cuny.hunter.streamrefactoring.core.wala.AnalysisUtils;
import edu.cuny.hunter.streamrefactoring.core.wala.CallStringWithReceivers;
import edu.cuny.hunter.streamrefactoring.core.wala.EclipseProjectAnalysisEngine;

@SuppressWarnings("restriction")
public final class Util {

	private static final class CorrespondingASTVisitor extends ASTVisitor {
		private MethodInvocation correspondingMethodInvocation;
		private MethodReference methodReference;
		private SourcePosition sourcePosition;
		private CompilationUnit unit;

		public CorrespondingASTVisitor(CompilationUnit unit, SourcePosition sourcePosition,
				MethodReference methodReference) {
			this.unit = unit;
			this.sourcePosition = sourcePosition;
			this.methodReference = methodReference;
		}

		public MethodInvocation getCorrespondingMethodInvocation() {
			return this.correspondingMethodInvocation;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			SimpleName methodName = node.getName();
			int extendedStartPosition = this.unit.getExtendedStartPosition(methodName);
			int lineNumber = this.unit.getLineNumber(extendedStartPosition);

			// FIXME: Matching line numbers could be dangerous. However, since
			// we should be using this for terminal operations, most likely,
			// this will work out. Otherwise, the matching can be inaccurate
			// (without column information).
			if (lineNumber == this.sourcePosition.getFirstLine())
				// we have at least a line correlation.
				if (matches(this.methodReference.getDeclaringClass(), this.methodReference, node)) {
					// found it.
					this.correspondingMethodInvocation = node;
					return false;
				}
			return true;
		}
	}

	private static final String BENCHMARK_ANNOTATION_NAME = "org.openjdk.jmh.annotations.Benchmark";

	private static final String BENCHMARK_SETUP_ANNOTATION_NAME = "org.openjdk.jmh.annotations.Setup";

	private static final String ENTRYPOINT_ANNOTATION_NAME = "edu.cuny.hunter.streamrefactoring.annotations.EntryPoint";

	private static final String FXML_ANNOTATION_NAME = "javafx.fxml.FXML";

	private static final Logger LOGGER = Logger.getLogger(LoggerNames.LOGGER_NAME);

	private static void addEntryPoint(Set<Entrypoint> result, final IMethod method, IClassHierarchy classHierarchy) {
		if (method != null) {
			Entrypoint entrypoint = new DefaultEntrypoint(method, classHierarchy);
			result.add(entrypoint);
		}
	}

	public static boolean allEqual(Collection<?> collection) {
		if (collection.isEmpty())
			return true;
		else {
			Object last = null;
			for (Object object : collection)
				if (last == null)
					last = object;
				else if (!object.equals(last))
					return false;
			return true;
		}
	}

	/**
	 * Returns true iff all of the predecessors of all of the given {@link CGNode}s
	 * in the {@link CallGraph} are {@link FakeRootMethod}s.
	 *
	 * @param nodes
	 *            The nodes whose predecessors to consider.
	 * @param callGraph
	 *            The {@link CallGraph} to search.
	 * @return True iff all of the predecessors of all of the given {@link CGNode}s
	 *         in the {@link CallGraph} are {@link FakeRootMethod}s.
	 * @apiNote The may be an issue here related to #106.
	 */
	@SuppressWarnings("unused")
	private static boolean allFake(Set<CGNode> nodes, CallGraph callGraph) {
		// for each node.
		for (CGNode cgNode : nodes)
			// for each predecessor.
			for (Iterator<CGNode> it = callGraph.getPredNodes(cgNode); it.hasNext();) {
				CGNode predNode = it.next();
				com.ibm.wala.classLoader.IMethod predMethod = predNode.getMethod();

				boolean isFakeMethod = predMethod instanceof FakeRootMethod
						|| predMethod instanceof FakeWorldClinitMethod;

				if (!isFakeMethod)
					return false;
			}
		return true;
	}

	public static Set<Entrypoint> findBenchmarkEntryPoints(IClassHierarchy classHierarchy) {
		return findEntryPoints(classHierarchy, tn -> isBenchmark(tn) || isBenchmarkSetup(tn));
	}

	private static MethodInvocation findCorrespondingMethodInvocation(CompilationUnit unit,
			SourcePosition sourcePosition, MethodReference method) {
		CorrespondingASTVisitor visitor = new CorrespondingASTVisitor(unit, sourcePosition, method);
		unit.accept(visitor);
		return visitor.getCorrespondingMethodInvocation();
	}

	/**
	 * Find all annotations and check whether they are "entry point." If yes, call
	 * new DefaultEntrypoint to get entry point, then, add it into the result set.
	 */
	public static Set<Entrypoint> findEntryPoints(IClassHierarchy classHierarchy) {
		final Set<Entrypoint> result = new HashSet<>();

		for (IClass klass : classHierarchy)
			if (!(isJDKClass(klass) || isLibraryClass(klass))) {
				boolean entryPointClass = false;
				boolean addedInstanceMethod = false;

				// iterate over all declared methods
				for (com.ibm.wala.classLoader.IMethod method : klass.getDeclaredMethods()) {
					// if method has an annotation
					if (!(method instanceof ShrikeCTMethod))
						throw new IllegalArgumentException("@EntryPoint only works for byte code.");

					try {
						for (Annotation annotation : ((ShrikeCTMethod) method).getAnnotations(true))
							if (isEntryPointClass(annotation.getType().getName())) {
								addEntryPoint(result, method, classHierarchy);
								entryPointClass = true;

								// if the method is an instance method.
								if (!method.isStatic())
									addedInstanceMethod = true;

								break;
							}
					} catch (InvalidClassFileException e) {
						throw new IllegalArgumentException(
								"Failed to find entry points using class hierarchy: " + classHierarchy + ".", e);
					}
				}

				if (entryPointClass) {
					// add any static initializers since the class will be loaded.
					addEntryPoint(result, klass.getClassInitializer(), classHierarchy);

					if (addedInstanceMethod) {
						// add a constructor.
						IMethod ctor = getUniqueConstructor(klass);
						addEntryPoint(result, ctor, classHierarchy);
					}
				}
			}

		return result;
	}

	/**
	 * Find entry points in the given {@link IClassHierarchy} whose annotations
	 * satisfy the given {@link Predicate} over annotation {@link TypeName}s.
	 * Processing is done on the class declaring the entry point method.
	 *
	 * @param classHierarchy
	 *            The {@link IClassHierarchy} to search through.
	 * @param predicate
	 *            The {@link Predicate} to test annotation {@link TypeName}s
	 *            against.
	 * @return A {@link Set} of {@link Entrypoint}s corresponding to methods
	 *         annotated with annotations satisfying the given predicate over type
	 *         names.
	 */
	public static Set<Entrypoint> findEntryPoints(IClassHierarchy classHierarchy, Predicate<TypeName> predicate) {
		final Set<Entrypoint> result = new HashSet<>();

		for (IClass klass : classHierarchy)
			if (!(isJDKClass(klass) || isLibraryClass(klass))) {
				boolean isAnnotationClass = false;
				// iterate over all declared methods
				for (com.ibm.wala.classLoader.IMethod method : klass.getDeclaredMethods()) {
					// if method has an annotation
					if (!(method instanceof ShrikeCTMethod))
						throw new IllegalArgumentException("@EntryPoint only works for byte code.");

					for (Annotation annotation : ((ShrikeCTMethod) method).getAnnotations()) {
						TypeName annotationName = annotation.getType().getName();

						if (predicate.test(annotationName)) {
							addEntryPoint(result, method, classHierarchy);
							isAnnotationClass = true;
							break;
						}
					}
				}

				if (isAnnotationClass) {
					// add static initializer.
					addEntryPoint(result, klass.getClassInitializer(), classHierarchy);

					// add default ctor.
					addEntryPoint(result, klass.getMethod(MethodReference.initSelector), classHierarchy);
				}
			}

		return result;
	}

	/**
	 * Find entry points from a {@link Set} of method signatures. No further
	 * processing is done of the class declaring the method.
	 */
	public static Set<Entrypoint> findEntryPoints(IClassHierarchy classHierarchy, Set<String> signatures) {
		final Set<Entrypoint> result = new HashSet<>();

		for (IClass klass : classHierarchy)
			if (!(isJDKClass(klass) || isLibraryClass(klass)))
				// iterate over all declared methods
				for (com.ibm.wala.classLoader.IMethod method : klass.getDeclaredMethods())
					if (signatures.contains(method.getSignature()))
						addEntryPoint(result, method, classHierarchy);
		return result;
	}

	/**
	 * Returns the index of the first {@link IMethod} in methods that is client
	 * code.
	 *
	 * @param methods
	 *            The {@link IMethod}s in question.
	 * @return The index of the first {@link IMethod} that is client code and -1 if
	 *         none found.
	 */
	public static int findIndexOfFirstClientMethod(IMethod[] methods) {
		for (int i = 0; i < methods.length; i++) {
			IMethod meth = methods[i];

			if (meth.getDeclaringClass().getClassLoader().getReference().equals(ClassLoaderReference.Application))
				return i;
		}

		return -1; // not found.
	}

	public static Set<Entrypoint> findJavaFXEntryPoints(IClassHierarchy classHierarchy) {
		return findEntryPoints(classHierarchy, tn -> isFXML(tn));
	}

	static Set<ITypeBinding> getAllInterfaces(ITypeBinding type) {
		Set<ITypeBinding> ret = new HashSet<>();
		ITypeBinding[] interfaces = type.getInterfaces();
		ret.addAll(Arrays.asList(interfaces));

		for (ITypeBinding interfaceBinding : interfaces)
			ret.addAll(getAllInterfaces(interfaceBinding));

		return ret;
	}

	static String getBinaryName(TypeReference typeReference) {
		TypeName name = typeReference.getName();
		String slashToDot = StringStuff.slashToDot(name.getPackage().toString() + "." + name.getClassName().toString());
		return slashToDot;
	}

	public static CallStringWithReceivers getCallString(CGNode node) {
		CallStringContext context = (CallStringContext) node.getContext();
		CallStringWithReceivers callString = (CallStringWithReceivers) context
				.get(CallStringContextSelector.CALL_STRING);
		return callString;
	}

	public static CallStringWithReceivers getCallString(InstanceKey instance) {
		NormalAllocationInNode allocationInNode = (NormalAllocationInNode) instance;
		return getCallString(allocationInNode);
	}

	public static CallStringWithReceivers getCallString(NormalAllocationInNode allocationInNode) {
		CGNode node = allocationInNode.getNode();
		return getCallString(node);
	}

	/**
	 * If it's a ctor, return the declaring class, otherwise, return the return
	 * type.
	 *
	 * @param method
	 *            The {@link IMethod} in question.
	 * @return The declaring class of target if target is a ctor and the return type
	 *         otherwise.
	 */
	public static TypeReference getEvaluationType(IMethod method) {
		// if it's a ctor.
		if (method.isInit())
			// then, use the declaring type.
			return method.getDeclaringClass().getReference();
		else // otherwise.
				// use the return type.
			return method.getReturnType();
	}

	static Set<ITypeBinding> getImplementedInterfaces(ITypeBinding type) {
		Set<ITypeBinding> ret = new HashSet<>();

		if (type.isInterface())
			ret.add(type);

		ret.addAll(getAllInterfaces(type));
		return ret;
	}

	public static JDTIdentityMapper getJDTIdentifyMapper(ASTNode node) {
		return new JDTIdentityMapper(JavaSourceAnalysisScope.SOURCE, node.getAST());
	}

	static int getLineNumberFromAST(SimpleName methodName) {
		CompilationUnit compilationUnit = (CompilationUnit) ASTNodes.getParent(methodName, ASTNode.COMPILATION_UNIT);
		int lineNumberFromAST = compilationUnit.getLineNumber(methodName.getStartPosition());
		return lineNumberFromAST;
	}

	static int getLineNumberFromIR(IBytecodeMethod method, SSAInstruction instruction)
			throws InvalidClassFileException {
		int bytecodeIndex = method.getBytecodeIndex(instruction.iindex);
		int lineNumberFromIR = method.getLineNumber(bytecodeIndex);
		return lineNumberFromIR;
	}

	/**
	 * This set is used to store visited values. Without this set, the method
	 * getPossibleTypes could be infinitely recursively called and would never
	 * return. So adding this set means to add a base case.
	 */
	static private HashSet<Value> seenValues = new HashSet<>();

	/**
	 * Use this method to get possible types instead of method getPossibleTypes().
	 */
	static Collection<TypeAbstraction> getAllPossibleTypes(int valueNumber, TypeInference inference) {
		seenValues.clear();
		return getPossibleTypes(valueNumber, inference);
	}

	static Collection<TypeAbstraction> getPossibleTypes(int valueNumber, TypeInference inference) {
		Set<TypeAbstraction> ret = new HashSet<>();
		Value value;
		try {
			value = inference.getIR().getSymbolTable().getValue(valueNumber);
		} catch (IllegalArgumentException exception) {
			LOGGER.info("The value number is invalid for getting possible types.");
			return ret;
		}

		// TODO: Should really be using a pointer analysis here rather than
		// re-implementing one using PhiValue.
		if (value instanceof PhiValue) {

			// avoid infinite recursion here
			if (seenValues.contains(value))
				return ret;
			else
				seenValues.add(value);

			// multiple possible types.
			PhiValue phiValue = (PhiValue) value;
			SSAPhiInstruction phiInstruction = phiValue.getPhiInstruction();
			int numberOfUses = phiInstruction.getNumberOfUses();
			// get the possible types for each use.
			for (int i = 0; i < numberOfUses; i++) {
				int use = phiInstruction.getUse(i);
				Collection<TypeAbstraction> possibleTypes = getPossibleTypes(use, inference);
				ret.addAll(possibleTypes);
			}
		} else
			// one one possible type.
			ret.add(inference.getType(valueNumber));

		return ret;
	}

	public static Collection<TypeAbstraction> getPossibleTypesInterprocedurally(Collection<CGNode> nodeCollection,
			int valueNumber, EclipseProjectAnalysisEngine<InstanceKey> engine, OrderingInference orderingInference)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException,
			UTFDataFormatException, JavaModelException {
		Collection<TypeAbstraction> ret = new HashSet<>();

		// for each call graph node.
		for (CGNode node : nodeCollection) {
			// get the pointer key.
			PointerKey valueKey = engine.getHeapGraph().getHeapModel().getPointerKeyForLocal(node, valueNumber);
			LOGGER.fine(() -> "Value pointer key is: " + valueKey);

			OrdinalSet<InstanceKey> pointsToSet = engine.getPointerAnalysis().getPointsToSet(valueKey);
			assert pointsToSet != null;
			LOGGER.fine(() -> "PointsTo set is: " + pointsToSet);

			for (InstanceKey instanceKey : pointsToSet) {
				IClass concreteClass = instanceKey.getConcreteType();

				if (!(concreteClass instanceof SyntheticClass)) {
					LOGGER.fine(() -> "Found non-synthetic concrete type: " + concreteClass);

					// Workaround #38, problem seemingly with generics.
					// Due to type erasure, we may have the problem if the return
					// type is java.lang.Object.
					// Find the return type of the instruction.
					TypeInference inference = TypeInference.make(node.getIR(), false);
					// Get all possible types
					Collection<TypeAbstraction> returnTypes = Util.getAllPossibleTypes(valueNumber, inference);

					// for each return type.
					for (TypeAbstraction rType : returnTypes) {
						PointType concreteType = new PointType(concreteClass);

						if (rType.getType().getReference().equals(TypeReference.JavaLangObject)) {
							IR ir = node.getIR();
							IMethod method = ir.getMethod();
							IBytecodeMethod bytecodeMethod = (IBytecodeMethod) method;

							// get the definition instruction.
							SSAInvokeInstruction def = (SSAInvokeInstruction) node.getDU().getDef(valueNumber);

							// which index is it into the instruction array?
							int instructionIndex = Util.indexOf(ir.getInstructions(), def);

							// get the bytecode index.
							int bytecodeIndex;
							try {
								bytecodeIndex = bytecodeMethod.getBytecodeIndex(instructionIndex);
							} catch (InvalidClassFileException e) {
								throw new IllegalArgumentException(
										"Value number: " + valueNumber + " does not have a definition ("
												+ instructionIndex + ") corresponding to a bytecode index.",
										e);
							}

							// get the source information
							SourcePosition sourcePosition;
							try {
								sourcePosition = method.getSourcePosition(bytecodeIndex);
							} catch (InvalidClassFileException e) {
								throw new IllegalArgumentException(
										"Value number: " + valueNumber + " does not have bytecode index ("
												+ bytecodeIndex + ") corresponding to a bytecode index.",
										e);
							}

							// let's assume that the source file is in the same project.
							IJavaProject enclosingProject = engine.getProject();

							String fqn = method.getDeclaringClass().getName().getPackage().toUnicodeString() + "."
									+ method.getDeclaringClass().getName().getClassName().toUnicodeString();
							IType type = enclosingProject.findType(fqn.replace('/', '.'));
							// FIXME: Need to (i) exclude from result timer and (ii) use the cache in
							// OptimizeStreamsRefactoringProcessor #141.
							CompilationUnit unit = RefactoringASTParser.parseWithASTProvider(type.getTypeRoot(), true,
									null);

							// We have the CompilationUnit corresponding to the instruction's file. Can we
							// correlate the instruction to the method invocation in the AST?
							MethodInvocation correspondingInvocation = findCorrespondingMethodInvocation(unit,
									sourcePosition, def.getCallSite().getDeclaredTarget());

							// what does the method return?
							ITypeBinding genericReturnType = correspondingInvocation.resolveMethodBinding()
									.getReturnType();

							// Is it compatible with the concrete type we got from WALA? But first, we'll
							// need to translate the Eclipse JDT type over to a IClass.
							TypeReference genericTypeRef = getJDTIdentifyMapper(correspondingInvocation)
									.getTypeRef(genericReturnType);
							IClass genericClass = node.getClassHierarchy().lookupClass(genericTypeRef);

							boolean assignableFrom = node.getClassHierarchy().isAssignableFrom(genericClass,
									concreteClass);

							// if it's assignable.
							if (assignableFrom)
								// would the ordering be consistent?
								if (wouldOrderingBeConsistent(Collections.unmodifiableCollection(ret), concreteType,
										orderingInference)) {
									// if so, add it.
									LOGGER.fine("Add type straight up: " + concreteType);
									ret.add(concreteType);
								} else {
									// otherwise, would the generic type cause the
									// ordering to be inconsistent?
									PointType genericType = new PointType(genericClass);

									if (wouldOrderingBeConsistent(Collections.unmodifiableCollection(ret), genericType,
											orderingInference)) {
										LOGGER.fine("Defaulting to generic type: " + genericType);
										ret.add(genericType);
									} else {
										// fall back to the concrete type.
										LOGGER.fine("Defaulting to concrete type eventhough it isn't consistent: "
												+ concreteType);
										ret.add(concreteType);
									}
								}
						} else {
							// just add it.
							LOGGER.fine("Add type straight up: " + concreteType);
							ret.add(concreteType);
						}
					}
				}
			}
		}
		return ret;
	}

	/**
	 * A null get value means that there is no unique ctor.
	 *
	 * @param klass
	 *            The {@link IClass} to find a unique ctor.
	 * @return The one and only ctor for klass and <code>null</code> if it doesn't
	 *         exist.
	 */
	private static IMethod getUniqueConstructor(IClass klass) {
		// try to find the default ctor.
		IMethod ctor = klass.getMethod(MethodReference.initSelector);

		// if not found, get all constructors.
		if (ctor == null) {
			Set<IMethod> allDeclaredConstructors = klass.getDeclaredMethods().stream()
					.filter(m -> m.getName().startsWith(MethodReference.initAtom)).collect(Collectors.toSet());

			// if there is a unique one.
			if (allDeclaredConstructors.size() == 1)
				// use that.
				ctor = allDeclaredConstructors.iterator().next();
		}

		return ctor;
	}

	static boolean implementsBaseStream(ITypeBinding type) {
		Set<ITypeBinding> implementedInterfaces = getImplementedInterfaces(type);
		return implementedInterfaces.stream()
				.anyMatch(i -> i.getErasure().getQualifiedName().equals("java.util.stream.BaseStream"));
	}

	/**
	 * @return true iff typeReference is a type that implements {@link BaseStream}.
	 */
	public static boolean implementsBaseStream(TypeReference typeReference, IClassHierarchy classHierarchy) {
		return implementsType(typeReference, classHierarchy, Util::isBaseStream);
	}

	public static boolean implementsCollector(TypeReference reference, IClassHierarchy classHierarchy) {
		return implementsType(reference, classHierarchy, Util::isCollector);
	}

	public static boolean implementsIterable(TypeReference reference, IClassHierarchy classHierarchy) {
		return implementsType(reference, classHierarchy, Util::isIterable);
	}

	public static boolean implementsType(TypeReference typeReference, IClassHierarchy classHierarchy,
			Predicate<IClass> predicate) {
		if (typeReference == null)
			return false;

		IClass clazz = classHierarchy.lookupClass(typeReference);

		if (clazz == null)
			return false;
		else
			return predicate.test(clazz) || clazz.getAllImplementedInterfaces().stream().anyMatch(predicate);
	}

	private static int indexOf(Object[] objs, Object o) {
		for (int i = 0; i < objs.length; i++) {
			if (o != null && o.equals(objs[i]))
				return i;
			if (o == null && objs[i] == null)
				return i;
		}
		return -1;
	}

	static boolean isAbstractType(Class<?> clazz) {
		// if it's an interface.
		if (clazz.isInterface())
			return true; // can't instantiate an interface.
		else if (Modifier.isAbstract(clazz.getModifiers()))
			return true; // can't instantiate an abstract type.
		else
			return false;
	}

	public static boolean isBaseStream(IClass clazz) {
		return Util.isType(clazz, "java/util/stream", "BaseStream");
	}

	private static boolean isBenchmark(TypeName typeName) {
		return AnalysisUtils.walaTypeNameToJavaName(typeName).equals(BENCHMARK_ANNOTATION_NAME);
	}

	private static boolean isBenchmarkSetup(TypeName typeName) {
		return AnalysisUtils.walaTypeNameToJavaName(typeName).equals(BENCHMARK_SETUP_ANNOTATION_NAME);
	}

	public static boolean isCollector(IClass clazz) {
		return Util.isType(clazz, "java/util/stream", "Collector");
	}

	/**
	 * check whether the annotation is "EntryPoint"
	 */
	private static boolean isEntryPointClass(TypeName typeName) {
		return AnalysisUtils.walaTypeNameToJavaName(typeName).equals(ENTRYPOINT_ANNOTATION_NAME);
	}

	private static boolean isFXML(TypeName typeName) {
		return AnalysisUtils.walaTypeNameToJavaName(typeName).equals(FXML_ANNOTATION_NAME);
	}

	public static boolean isIterable(IClass clazz) {
		return Util.isType(clazz, "java/lang", "Iterable");
	}

	public static boolean isScalar(Collection<TypeAbstraction> types) {
		Boolean ret = null;

		for (TypeAbstraction typeAbstraction : types) {
			boolean scalar = isScalar(typeAbstraction);

			if (ret == null)
				ret = scalar;
			else if (ret != scalar)
				throw new IllegalArgumentException("Inconsistent types: " + types);
		}

		return ret;
	}

	public static boolean isScalar(TypeAbstraction typeAbstraction) {
		TypeReference typeReference = typeAbstraction.getTypeReference();

		if (typeReference.isArrayType())
			return false;
		else if (typeReference.equals(TypeReference.Void))
			throw new IllegalArgumentException("Void is neither scalar or nonscalar.");
		else if (typeReference.isPrimitiveType())
			return true;
		else if (typeReference.isReferenceType()) {
			IClass type = typeAbstraction.getType();
			return !edu.cuny.hunter.streamrefactoring.core.analysis.Util.isIterable(type)
					&& type.getAllImplementedInterfaces().stream().noneMatch(Util::isIterable);
		} else
			throw new IllegalArgumentException("Can't tell if type is scalar: " + typeAbstraction);
	}

	static boolean isType(IClass clazz, String packagePath, String typeName) {
		if (clazz.isInterface()) {
			Atom typePackage = clazz.getName().getPackage();
			Atom compareToPackage = Atom.findOrCreateUnicodeAtom(packagePath);
			if (typePackage.equals(compareToPackage)) {
				Atom className = clazz.getName().getClassName();
				Atom compareToClass = Atom.findOrCreateUnicodeAtom(typeName);
				if (className.equals(compareToClass))
					return true;
			}
		}
		return false;
	}

	public static boolean matches(SSAInstruction instruction, MethodInvocation invocation, Optional<Logger> logger) {
		if (instruction.hasDef() && instruction.getNumberOfDefs() == 2) {
			if (instruction instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction invokeInstruction = (SSAInvokeInstruction) instruction;
				if (matches(invokeInstruction.getDeclaredTarget().getDeclaringClass(),
						invokeInstruction.getCallSite().getDeclaredTarget(), invocation))
					return true;
			} else
				logger.ifPresent(l -> l.warning("Instruction: " + instruction + " is not an SSAInstruction."));
		} else
			logger.ifPresent(l -> l.warning("Instruction: " + instruction + " has no definitions."));
		return false;
	}

	private static boolean matches(TypeReference methodDeclaringType, MethodReference method,
			MethodInvocation invocation) {
		if (getBinaryName(methodDeclaringType).equals(invocation.getExpression().resolveTypeBinding().getBinaryName()))
			// FIXME: This matching needs much work #153.
			if (method.getName().toString().equals(invocation.resolveMethodBinding().getName()))
				return true;
		return false;
	}

	private static boolean wouldOrderingBeConsistent(final Collection<TypeAbstraction> types,
			TypeAbstraction additionalType, OrderingInference inference)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		// make a copy.
		Collection<TypeAbstraction> copy = new ArrayList<>(types);

		// add the new type to the copy.
		copy.add(additionalType);

		// check the ordering
		try {
			inference.inferOrdering(copy);
		} catch (InconsistentPossibleOrderingException | NoninstantiableException e) {
			// it's inconsistent.
			return false;
		}

		return true;
	}

	private Util() {
	}

	/**
	 * This method is used to check whether the CGNode is a "stream node". It checks
	 * each instruction in the node. If the type of instruction could implement base
	 * stream, then it is a stream node.
	 * 
	 * @param node:
	 *            CGNode
	 * @param classHierarchy
	 */
	public static boolean isStreamNode(CGNode node, IClassHierarchy classHierarchy) {
		if (isDeclaredStreamClass(node, classHierarchy))
			return true;

		IR ir = node.getIR();

		if (ir == null || ir.isEmptyIR())
			return true;

		for (SSAInstruction instruction : ir.getInstructions()) {
			if (instruction == null)
				continue;

			// Most of instruction APIs provide the methods to get the return types.
			StreamFindingVisitor visitor = new StreamFindingVisitor(classHierarchy);
			instruction.visit(visitor);

			if (visitor.hasFoundStream())
				return true;

			// otherwise, let's check the defs and uses.
			TypeInference inference = TypeInference.make(ir, false);

			Stream<TypeAbstraction> defs = IntStream.range(0, instruction.getNumberOfDefs())
					.mapToObj(i -> instruction.getDef(i)).flatMap(d -> getAllPossibleTypes(d, inference).stream());

			Stream<TypeAbstraction> uses = IntStream.range(0, instruction.getNumberOfUses())
					.mapToObj(i -> instruction.getUse(i)).flatMap(u -> getAllPossibleTypes(u, inference).stream());

			if (Stream.concat(defs, uses).anyMatch(t -> implementsBaseStream(t.getTypeReference(), classHierarchy)))
				return true;
		}

		return false;
	}

	/**
	 * Check declared class for CGNode
	 * 
	 * @param node:
	 *            CGNode in the CallGraph
	 */
	private static boolean isDeclaredStreamClass(CGNode node, IClassHierarchy classHierarchy) {
		IMethod method = node.getMethod();
		if (implementsBaseStream(method.getDeclaringClass().getReference(), classHierarchy))
			return true;
		else
			return false;
	}
}