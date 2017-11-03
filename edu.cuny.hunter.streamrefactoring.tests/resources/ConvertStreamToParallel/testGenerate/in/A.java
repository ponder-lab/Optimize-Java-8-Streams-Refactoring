package p;

import java.util.stream.Stream;

class A {
	void m() {
		Stream.generate(() -> 1).count();		
	}
}