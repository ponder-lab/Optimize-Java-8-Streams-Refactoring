package p;

import javax.annotation.Generated;

interface I {
	@Deprecated
	@Generated("hello")
	void m();
}

abstract class A implements I {
	@Generated("hello")
	@Deprecated
	public void m() {
	}
}