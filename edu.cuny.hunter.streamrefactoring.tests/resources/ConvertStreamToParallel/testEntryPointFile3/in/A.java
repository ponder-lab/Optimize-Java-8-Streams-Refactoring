package p;

import java.util.HashSet;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m() {
		HashSet h1 = new HashSet();
		h1.stream().count();
	}
}
