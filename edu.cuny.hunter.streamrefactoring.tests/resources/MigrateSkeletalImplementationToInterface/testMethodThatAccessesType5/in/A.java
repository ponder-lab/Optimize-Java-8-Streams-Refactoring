package p;

interface I {
	<E extends B> void m();
}

class B {
}

public abstract class A implements I {

	@Override
	public <E extends B> void m() {
		E e;
	}
}
