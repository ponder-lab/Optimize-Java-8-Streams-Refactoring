package p;

import java.util.stream.Stream;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m() {
		Stream.generate(() -> 1).count();		
	}
}