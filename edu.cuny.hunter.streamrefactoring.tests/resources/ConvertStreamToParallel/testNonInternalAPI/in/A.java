package p;

import java.util.HashSet;
import java.util.stream.*;

class A {
	Stream<Object> m() {
		Stream<Object> stream = new HashSet<>().parallelStream();
		return stream;
	}

	void n() {
		Stream<Object> s = m();
		s.count();
	}
}