package p;

interface I<E extends B> {
	void m();
}

class B {
}

public abstract class A<E extends B> implements I<E> {

	@Override
	public void m() {
		E e;
	}
}
