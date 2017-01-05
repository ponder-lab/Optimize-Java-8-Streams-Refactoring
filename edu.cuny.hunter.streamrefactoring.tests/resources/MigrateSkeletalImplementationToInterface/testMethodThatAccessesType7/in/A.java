package p;

interface I {
	void m();
}

class B {
}

public abstract class A<E extends B> implements I {

	@Override
	public void m() {
		E e;
	}
}
