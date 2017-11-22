package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m(HashSet<Object> h) {
		Stream<Object> stream = h.parallelStream();
		stream.distinct().count();
	}
}