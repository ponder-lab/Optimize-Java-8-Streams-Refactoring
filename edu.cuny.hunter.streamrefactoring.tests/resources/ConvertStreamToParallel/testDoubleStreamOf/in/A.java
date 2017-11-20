package p;

import java.util.stream.DoubleStream;

public class A {
	@EntryPoint
	void m() {
		DoubleStream.of(1.111)
	    .count();
	}
}