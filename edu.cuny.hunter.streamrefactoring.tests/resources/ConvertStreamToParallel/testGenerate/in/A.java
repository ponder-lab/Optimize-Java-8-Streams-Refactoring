package p;

import java.util.stream.Stream;

class A {
	@EntryPoint
	void m() {
		Stream.generate(() -> 1).count();		
	}
}