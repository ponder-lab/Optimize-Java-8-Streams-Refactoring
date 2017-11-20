package p;

import java.util.stream.Stream;

public class A {
	@EntryPoint
	void m() {
		Stream.of("a").count();
	}
}
