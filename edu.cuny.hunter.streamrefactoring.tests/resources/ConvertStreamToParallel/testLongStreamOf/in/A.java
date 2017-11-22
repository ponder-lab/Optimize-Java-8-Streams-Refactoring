package p;

import java.util.stream.LongStream;

import edu.cuny.hunter.streamrefactoring.annotations.*;

public class A {
	@EntryPoint
	void m() {
		LongStream.of(1111)
	    .count();
	}
}