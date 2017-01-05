package p;

class C<E> {
}

interface I<T> {
	default void m(C<T> c) {
		m(new C<T>());
	}
}

abstract class A<E> implements I<E> {
}