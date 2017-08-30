package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.BaseStream;

import com.ibm.wala.analysis.typeInference.PointType;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.PhiValue;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.Value;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.StringStuff;

public final class Util {

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
			HeapModel heapModel, PointerAnalysis<InstanceKey> pointerAnalysis, Logger logger) {
		Collection<TypeAbstraction> ret = new HashSet<>();

		PointerKey valueKey = heapModel.getPointerKeyForLocal(node, valueNumber);
		logger.fine(() -> "Value pointer key is: " + valueKey);
		
		OrdinalSet<InstanceKey> pointsToSet = pointerAnalysis.getPointsToSet(valueKey);
		assert pointsToSet != null;
		logger.fine(() -> "PointsTo set is: " + pointsToSet);

		for (InstanceKey instanceKey : pointsToSet) {
			IClass concreteType = instanceKey.getConcreteType();
			if (!(concreteType instanceof SyntheticClass)) {
				logger.fine(() -> "Found non-synthetic concrete type: " + concreteType);
				PointType pointType = new PointType(concreteType);
				ret.add(pointType);
			}
		}

		return ret;
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
}
