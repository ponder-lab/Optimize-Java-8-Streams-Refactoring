package p;

import java.util.ArrayList;

import edu.cuny.hunter.streamrefactoring.annotations.EntryPoint;

class A {
	@EntryPoint
	void m() {
		new A();
	}

	A() {
		new ArrayList().stream().count();
	}
}