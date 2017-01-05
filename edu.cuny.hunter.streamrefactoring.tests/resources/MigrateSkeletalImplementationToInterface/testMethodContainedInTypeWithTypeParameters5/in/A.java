package p;

interface I<X,T> {
	void m();
}

abstract class A<X,E> implements I<X,E> {
	@Override
	public void m() {
		E e = null;
	}
}
