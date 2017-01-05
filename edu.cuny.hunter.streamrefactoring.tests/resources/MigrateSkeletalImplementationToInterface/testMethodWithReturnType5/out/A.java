package p;

class C<E> {
}

interface I<T> {
	default C<T> m() {
		return new C<Integer>();
	}
}

abstract class A implements I<Integer> {
}
