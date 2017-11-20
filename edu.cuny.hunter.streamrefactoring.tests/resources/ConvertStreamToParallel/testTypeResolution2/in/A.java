package p;

import java.util.HashSet;
import java.util.Set;

class A {
	@EntryPoint
	void m() {
		Set<Object> anotherSet = new HashSet<>();
		anotherSet.parallelStream().count();
	}
}