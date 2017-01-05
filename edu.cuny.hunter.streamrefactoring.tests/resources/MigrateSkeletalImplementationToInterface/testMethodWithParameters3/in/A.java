package p;

class C<E> {
}

interface I<T> {
	void m(C<T> c);
}

abstract class A<E> implements I<E> {
	@Override
	public void m(C<E> c) {
		m(new C<E>());
	}
}