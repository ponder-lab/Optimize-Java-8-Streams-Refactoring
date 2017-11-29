package p;

import java.util.stream.DoubleStream;

import edu.cuny.hunter.streamrefactoring.annotations.*;

public class A {
	@EntryPoint
	void n() {
		m();
		m();
	}

	void m() {
		DoubleStream.of(1.111).count();
	}
}