package p;

import java.util.stream.LongStream;

public class A {
	@EntryPoint
	void m() {
		LongStream.of(1111)
	    .count();
	}
}