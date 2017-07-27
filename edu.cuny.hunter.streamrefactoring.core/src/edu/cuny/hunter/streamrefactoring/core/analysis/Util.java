package edu.cuny.hunter.streamrefactoring.core.analysis;

import java.lang.reflect.Modifier;

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

}
