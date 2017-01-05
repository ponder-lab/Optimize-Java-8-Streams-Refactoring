package p;

class C<E> {
}

interface I<T> {
	C<T> m();
}

abstract class A implements I<Integer> {
	@Override
	public C<Integer> m() {
		return new C<Integer>();
	}
}
