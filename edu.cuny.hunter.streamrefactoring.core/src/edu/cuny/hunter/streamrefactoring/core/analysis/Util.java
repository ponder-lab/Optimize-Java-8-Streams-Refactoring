package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.BaseStream;

import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;

import com.ibm.wala.analysis.typeInference.PointType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.JDTIdentityMapper;
import com.ibm.wala.classLoader.IBytecodeMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IMethod.SourcePosition;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.PhiValue;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.Value;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.StringStuff;

import edu.cuny.hunter.streamrefactoring.core.utils.LoggerNames;

public final class Util {

	private static final Logger LOGGER =  Logger.getLogger(LoggerNames.LOGGER_NAME);

	private static final class CorrespondingASTVisitor extends ASTVisitor {
		private CompilationUnit unit;
		private SourcePosition sourcePosition;
		private MethodReference methodReference;
		private MethodInvocation correspondingMethodInvocation;

		public CorrespondingASTVisitor(CompilationUnit unit, SourcePosition sourcePosition,
				MethodReference methodReference) {
			this.unit = unit;
			this.sourcePosition = sourcePosition;
			this.methodReference = methodReference;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			SimpleName methodName = node.getName();
			int extendedStartPosition = unit.getExtendedStartPosition(methodName);
			int lineNumber = unit.getLineNumber(extendedStartPosition);

			// FIXME: Matching line numbers could be dangerous. However, since
			// we should be using this for terminal operations, most likely,
			// this will work out. Otherwise, the matching can be inaccurate
			// (without column information).
			if (lineNumber == sourcePosition.getFirstLine()) {
				// we have at least a line correlation.
				if (matches(methodReference.getDeclaringClass(), methodReference, node)) {
					// found it.
					correspondingMethodInvocation = node;
					return false;
				}
			}
			return true;
		}

		public MethodInvocation getCorrespondingMethodInvocation() {
			return correspondingMethodInvocation;
		}
	}

	private Util() {
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

	static Collection<TypeAbstraction> getPossibleTypes(int valueNumber, TypeInference inference) {
		Set<TypeAbstraction> ret = new HashSet<>();
		Value value = inference.getIR().getSymbolTable().getValue(valueNumber);

		// TODO: Should really be using a pointer analysis here rather than
		// re-implementing one using PhiValue.
		if (value instanceof PhiValue) {
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
		} else {
			// one one possible type.
			ret.add(inference.getType(valueNumber));
		}

		return ret;
	}

	static boolean isBaseStream(IClass clazz) {
		return isType(clazz, "java/util/stream", "BaseStream");
	}

	static boolean isCollector(IClass clazz) {
		return isType(clazz, "java/util/stream", "Collector");
	}

	static boolean isIterable(IClass clazz) {
		return isType(clazz, "java/lang", "Iterable");
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

	/**
	 * @return true iff typeReference is a type that implements
	 *         {@link BaseStream}.
	 */
	public static boolean implementsBaseStream(TypeReference typeReference, IClassHierarchy classHierarchy) {
		return implementsType(typeReference, classHierarchy, Util::isBaseStream);
	}

	public static boolean implementsIterable(TypeReference reference, IClassHierarchy classHierarchy) {
		return implementsType(reference, classHierarchy, Util::isIterable);
	}

	public static boolean implementsCollector(TypeReference reference, IClassHierarchy classHierarchy) {
		return implementsType(reference, classHierarchy, Util::isCollector);
	}

	public static boolean implementsType(TypeReference typeReference, IClassHierarchy classHierarchy,
			Predicate<IClass> predicate) {
		IClass clazz = classHierarchy.lookupClass(typeReference);

		if (clazz == null)
			return false;
		else
			return predicate.test(clazz) || clazz.getAllImplementedInterfaces().stream().anyMatch(predicate);
	}

	static String getBinaryName(TypeReference typeReference) {
		TypeName name = typeReference.getName();
		String slashToDot = StringStuff.slashToDot(name.getPackage().toString() + "." + name.getClassName().toString());
		return slashToDot;
	}

	public static Collection<TypeAbstraction> getPossibleTypesInterprocedurally(CGNode node, int valueNumber,
			HeapModel heapModel, PointerAnalysis<InstanceKey> pointerAnalysis, Stream stream, Logger logger)
			throws NoniterableException, NoninstantiableException, CannotExtractSpliteratorException {
		Collection<TypeAbstraction> ret = new HashSet<>();

		PointerKey valueKey = heapModel.getPointerKeyForLocal(node, valueNumber);
		logger.fine(() -> "Value pointer key is: " + valueKey);

		OrdinalSet<InstanceKey> pointsToSet = pointerAnalysis.getPointsToSet(valueKey);
		assert pointsToSet != null;
		logger.fine(() -> "PointsTo set is: " + pointsToSet);

		for (InstanceKey instanceKey : pointsToSet) {
			IClass concreteClass = instanceKey.getConcreteType();

			if (!(concreteClass instanceof SyntheticClass)) {
				logger.fine(() -> "Found non-synthetic concrete type: " + concreteClass);

				// Workaround #38, problem seemingly with generics.
				// Due to type erasure, we may have the problem if the return
				// type is java.lang.Object.
				// Find the return type of the instruction.
				TypeInference inference = TypeInference.make(node.getIR(), false);
				Collection<TypeAbstraction> returnTypes = Util.getPossibleTypes(valueNumber, inference);
				assert returnTypes.size() == 1 : "Not expecting more than one return type.";
				TypeAbstraction rType = returnTypes.iterator().next();

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
								"Value number: " + valueNumber + " does not have a definition (" + instructionIndex
										+ ") corresponding to a bytecode index.",
								e);
					}

					// get the source information
					SourcePosition sourcePosition;
					try {
						sourcePosition = method.getSourcePosition(bytecodeIndex);
					} catch (InvalidClassFileException e) {
						throw new IllegalArgumentException(
								"Value number: " + valueNumber + " does not have bytecode index (" + bytecodeIndex
										+ ") corresponding to a bytecode index.",
								e);
					}

					// ensure that the file names are the same.
					// FIXME: Do we need to worry about paths? Maybe it would
					// suffice to check packages.
					CompilationUnit unit = stream.getEnclosingCompilationUnit();
					ITypeRoot typeRoot = unit.getTypeRoot();
					String typeRootFileName = typeRoot.getElementName();
					String sourcePositionFileName = sourcePosition.getFileName();

					if (typeRootFileName.equals(sourcePositionFileName)) {
						// same file.
						// we have the CompilationUnit corresponding to the
						// instruction's file.
						// can we correlate the instruction to the method
						// invocation in the AST?
						MethodInvocation correspondingInvocation = findCorrespondingMethodInvocation(unit,
								sourcePosition, def.getCallSite().getDeclaredTarget());

						// what does the method return?
						ITypeBinding genericReturnType = correspondingInvocation.resolveMethodBinding().getReturnType();

						// Is it compatible with the concrete type we got from
						// WALA?
						// But first, we'll need to translate the Eclipse JDT
						// type over to a IClass.
						TypeReference genericTypeRef = getJDTIdentifyMapper(correspondingInvocation)
								.getTypeRef(genericReturnType);
						IClass genericClass = node.getClassHierarchy().lookupClass(genericTypeRef);

						boolean assignableFrom = node.getClassHierarchy().isAssignableFrom(genericClass, concreteClass);

						// if it's assignable.
						if (assignableFrom) {
							// would the ordering be consistent?
							if (wouldOrderingBeConsistent(Collections.unmodifiableCollection(ret), concreteType,
									stream.getOrderingInference())) {
								// if so, add it.
								LOGGER.info("Add type straight up: " + concreteType);
								ret.add(concreteType);
							} else {
								// otherwise, would the generic type cause the
								// ordering to be inconsistent?
								PointType genericType = new PointType(genericClass);

								if (wouldOrderingBeConsistent(Collections.unmodifiableCollection(ret), genericType,
										stream.getOrderingInference())) {
									LOGGER.info("Defaulting to generic type: " + genericType);
									ret.add(genericType);
								} else {
									// fall back to the concrete type.
									LOGGER.info("Defaulting to concrete type eventhough it isn't consistent: "
											+ concreteType);
									ret.add(concreteType);
								}
							}
						}

					} else {
						// FIXME: Interprocedural?
						throw new IllegalStateException("Can't find corresponding file.");
					}
				} else {
					// just add it.
					LOGGER.info("Add type straight up: " + concreteType);
					ret.add(concreteType);
				}
			}
		}
		return ret;
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

	private static MethodInvocation findCorrespondingMethodInvocation(CompilationUnit unit,
			SourcePosition sourcePosition, MethodReference method) {
		CorrespondingASTVisitor visitor = new CorrespondingASTVisitor(unit, sourcePosition, method);
		unit.accept(visitor);
		return visitor.getCorrespondingMethodInvocation();
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

	public static boolean allEqual(Collection<?> collection) {
		if (collection.isEmpty())
			return true;
		else {
			Object last = null;
			for (Object object : collection) {
				if (last == null)
					last = object;
				else if (!object.equals(last))
					return false;
			}
			return true;
		}
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
		if (getBinaryName(methodDeclaringType)
				.equals(invocation.getExpression().resolveTypeBinding().getBinaryName())) {
			// FIXME: This matching needs much work.
			if (method.getName().toString().equals(invocation.resolveMethodBinding().getName())) {
				return true;
			}
		}
		return false;
	}

	public static JDTIdentityMapper getJDTIdentifyMapper(ASTNode node) {
		return new JDTIdentityMapper(JavaSourceAnalysisScope.SOURCE, node.getAST());
	}
}
