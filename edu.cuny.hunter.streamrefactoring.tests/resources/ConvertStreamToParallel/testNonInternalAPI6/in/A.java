package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {

	Stream<Object> m() {
		Stream<Object> stream = new HashSet<>().stream().parallel();
		return stream;
	}

	void n(Stream<Object> s) {
		s.distinct().count();
	}

	@EntryPoint
	public void main(String[] args) {
		n(m());
	}
}