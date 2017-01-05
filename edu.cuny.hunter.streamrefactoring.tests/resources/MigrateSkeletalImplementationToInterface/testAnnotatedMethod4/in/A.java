package p;

import javax.annotation.Generated;

interface I {
	@Generated("goodbye")
	void m();
}

abstract class A implements I {
	@Generated("hello")
	public void m() {
	}
}