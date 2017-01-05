package p;

import javax.annotation.Generated;

interface I {
	void m(@Generated("hello") @SuppressWarnings("goodbye") int n);
}

abstract class A implements I {
	public void m(@Generated("hello") @SuppressWarnings("solong") int n) {
	}
}