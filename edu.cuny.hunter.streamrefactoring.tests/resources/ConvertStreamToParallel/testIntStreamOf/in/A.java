package p;

import java.util.stream.IntStream;

public class A {
	@EntryPoint
	void m() {
		IntStream.of(1)
	    .count();
	}
}