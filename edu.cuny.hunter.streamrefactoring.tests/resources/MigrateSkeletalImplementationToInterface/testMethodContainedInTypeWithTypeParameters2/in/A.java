package p;

interface I {
	void m();
}

abstract class A<E> implements I {
	public void m() {
		E e = null;
	}
}
