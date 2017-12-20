package p;

import java.util.HashSet;
import java.util.stream.*;

import edu.cuny.hunter.streamrefactoring.annotations.*;

/**
 * remove an entry point before m() from testMultipleEntryPoint1
 */
class A {

	Stream<Object> m() {
		Stream<Object> stream = new HashSet<>().parallelStream();
		return stream;
	}

	@EntryPoint
	void n() {
		Stream<Object> s = m();
		s.count();
	}
}