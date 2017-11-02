package p;

import java.util.stream.Stream;

public class A {
	
	void m() {
		Stream.of("a")
	    .forEach(System.out::println);
	}
}
