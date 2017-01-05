package p;

import javax.annotation.Generated;

interface I {
	void m(@Generated("hello") int n);
}

abstract class A implements I {
	public void m(@Generated("hello") int n) {
	}
}