package p;

import java.util.HashSet;
import java.util.Set;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	void m() {
		Set<Object> anotherSet = new HashSet<>();
		anotherSet.parallelStream();
	}
}