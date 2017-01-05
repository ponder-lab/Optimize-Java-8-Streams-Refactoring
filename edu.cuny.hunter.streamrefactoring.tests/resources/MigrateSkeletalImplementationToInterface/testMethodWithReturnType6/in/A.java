package p;

class C<E> {
}

interface I<T> {
	C<T> m();
}

abstract class A<S> implements I<S> {
	@Override
	public C<S> m() {
		return new C<S>();
	}
}
