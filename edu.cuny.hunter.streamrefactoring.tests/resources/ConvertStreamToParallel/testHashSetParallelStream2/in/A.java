package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m() {
		Stream<Object> stream = new HashSet<>().parallelStream();
		stream.distinct().count();
	}
}