package p;

import java.util.Collection;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m(Collection<Object> h) {
		Stream<Object> stream = h.parallelStream();
		stream.distinct().count();
	}
}