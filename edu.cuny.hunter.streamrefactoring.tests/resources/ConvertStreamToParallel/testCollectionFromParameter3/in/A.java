package p;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void n() {
		m(new HashSet<Object>());
	}

	void m(Collection<Object> h) {
		Stream<Object> stream = h.parallelStream();
		stream.distinct().count();
	}
}
