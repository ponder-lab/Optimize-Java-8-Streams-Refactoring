package p;

import java.util.HashSet;
import java.util.stream.*;

class A {
	void n() {
		m(new HashSet<Object>());
	}
	
	void m(HashSet<Object> h) {
		Stream<Object> stream = h.parallelStream();
		stream.distinct().count();
	}
}