package p;

import java.util.HashSet;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m() {
		new HashSet().stream().count();
	}
	
	@EntryPoint
	void n() {
		new HashSet().stream().count();
	}
}