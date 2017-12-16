package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {

	Stream<Object> m() {
		Stream<Object> stream = new HashSet<>().stream().parallel();
		return stream;
	}

	@EntryPoint
	void n() {
		Stream<Object> s = m();
		s.distinct().count();
	}
}