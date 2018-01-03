package p;

import java.util.stream.IntStream;

import edu.cuny.hunter.streamrefactoring.annotations.*;

public class A {
	@EntryPoint
	void m() {
		IntStream.of(1)
	    .count();
	}
}