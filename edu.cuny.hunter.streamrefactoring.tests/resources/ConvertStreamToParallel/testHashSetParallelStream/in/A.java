package p;

import java.util.HashSet;
import java.util.stream.*;

class A {
	@EntryPoint
	void m() {
		Stream<Object> stream = new HashSet<>().parallelStream();
	}
}