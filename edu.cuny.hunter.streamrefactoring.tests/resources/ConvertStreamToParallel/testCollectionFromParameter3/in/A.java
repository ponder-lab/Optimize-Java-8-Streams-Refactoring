package p;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.*;

class A {
	@EntryPoint
	void n() {
		m(new HashSet<Object>());
	}

	@EntryPoint
	void m(Collection<Object> h) {
		Stream<Object> stream = h.parallelStream();
		stream.distinct().count();
	}
}