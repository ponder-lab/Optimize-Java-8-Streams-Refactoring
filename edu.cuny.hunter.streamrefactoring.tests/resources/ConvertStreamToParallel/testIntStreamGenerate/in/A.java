package p;

import java.util.stream.IntStream;

import edu.cuny.hunter.streamrefactoring.annotations.*;

class A {
	@EntryPoint
	void m() {
		IntStream.generate(() -> 1).count();		
	}
}