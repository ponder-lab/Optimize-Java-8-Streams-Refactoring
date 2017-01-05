package p;

import javax.annotation.Generated;

interface I {
	void m(@Deprecated @Generated("hello") int n);
}

abstract class A implements I {
	public void m(@Deprecated @Generated("goodbye") int n) {
	}
}