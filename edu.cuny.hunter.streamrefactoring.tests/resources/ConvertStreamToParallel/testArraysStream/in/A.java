package p;

import java.util.Arrays;

class A {
	@EntryPoint
	void m() {
		Arrays.stream(new Object[1]);
	}
}