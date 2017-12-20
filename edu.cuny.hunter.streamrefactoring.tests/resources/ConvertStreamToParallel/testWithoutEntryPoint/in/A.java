package p;

import java.util.HashSet;

class A {

	void m() {
		HashSet h1 = new HashSet();
		h1.stream().count();
	}

	void n() {
		HashSet h2 = new HashSet();
		h2.stream().count();
	}
}