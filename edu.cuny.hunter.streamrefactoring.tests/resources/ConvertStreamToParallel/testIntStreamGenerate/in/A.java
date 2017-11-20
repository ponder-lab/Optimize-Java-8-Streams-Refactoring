package p;

import java.util.stream.IntStream;

class A {
	@EntryPoint
	void m() {
		IntStream.generate(() -> 1).count();		
	}
}