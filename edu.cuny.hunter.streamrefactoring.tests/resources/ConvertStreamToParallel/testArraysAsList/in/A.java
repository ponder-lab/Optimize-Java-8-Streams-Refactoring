package p;

import java.util.Arrays;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m() {
		Arrays.asList().stream().count();
	}
}