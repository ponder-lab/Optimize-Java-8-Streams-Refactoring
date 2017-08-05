package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.PhiValue;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.Value;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.StringStuff;

final class Util {

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
		if (clazz.isInterface()) {
			Atom typePackage = clazz.getName().getPackage();
			Atom streamPackage = Atom.findOrCreateUnicodeAtom("java/util/stream");
			if (typePackage.equals(streamPackage)) {
				Atom className = clazz.getName().getClassName();
				Atom baseStream = Atom.findOrCreateUnicodeAtom("BaseStream");
				if (className.equals(baseStream))
					return true;
			}
		}
		return false;
	}

	static boolean isBaseStream(TypeReference returnType, IClassHierarchy classHierarchy) {
		IClass clazz = classHierarchy.lookupClass(returnType);
		
		if (clazz == null)
			return false;
		else
			return isBaseStream(clazz);
	}

	static String getBinaryName(TypeReference typeReference) {
		TypeName name = typeReference.getName();
		String slashToDot = StringStuff.slashToDot(name.getPackage().toString() + "." + name.getClassName().toString());
		return slashToDot;
	}
}
