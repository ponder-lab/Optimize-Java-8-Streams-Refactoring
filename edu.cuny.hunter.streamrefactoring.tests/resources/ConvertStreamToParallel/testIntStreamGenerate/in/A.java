package p;

import java.util.stream.IntStream;

class A {
	void m() {
		IntStream.generate(() -> 1).count();		
	}
}