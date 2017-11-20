package p;

import java.util.Collection;
import java.util.stream.*;

class A {
	@EntryPoint
	void m(Collection<Object> h) {
		Stream<Object> stream = h.parallelStream();
		stream.distinct().count();
	}
}