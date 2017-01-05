package p;

interface I<T> {
	void m();
}

abstract class A<E> implements I<E> {
	@Override
	public void m() {
		E e = null;
	}
}
