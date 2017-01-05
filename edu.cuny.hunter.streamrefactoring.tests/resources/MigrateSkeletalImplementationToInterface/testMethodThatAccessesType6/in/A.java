package p;

import p.A.B;

interface I {
	<E extends B> void m();
}

public abstract class A implements I {

	class B {
	}

	@Override
	public <E extends B> void m() {
		E e;
	}
}
