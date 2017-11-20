package p;

import java.util.HashSet;
import java.util.stream.*;

class A {
	@EntryPoint
	void n() {
		m(new HashSet<Object>());
	}
	
	@EntryPoint
	void m(HashSet<Object> h) {
		Stream<Object> stream = h.parallelStream();
		stream.distinct().count();
	}
}