package p;

import java.util.HashSet;

import edu.cuny.hunter.streamrefactoring.annotations.*;

/**
 * remove an annotation before m() from testMultipleEntryPoint
 */
class A {

	void m() {
		HashSet h1 = new HashSet();
		h1.stream().count();
	}

	@EntryPoint
	void n() {
		HashSet h2 = new HashSet();
		h2.stream().count();
	}
}