package p;

import java.util.stream.Stream;

import edu.cuny.hunter.streamrefactoring.annotations.*;

public class A {
	@EntryPoint
	void m() {
		Stream.of("a").count();
	}
}
