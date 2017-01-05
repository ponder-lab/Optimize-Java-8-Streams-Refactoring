package p;

class C<E> {
}

interface I<T> {
	default void m(C<T> c) {
		m(new C<Integer>());
	}
}

abstract class A implements I<Integer> { 
}
