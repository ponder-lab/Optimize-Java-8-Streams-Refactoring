package p;

class C<E> {
}

interface I<T> {
	default C<T> m() {
		return new C<T>();
	}
}

abstract class A<S> implements I<S> {
}
