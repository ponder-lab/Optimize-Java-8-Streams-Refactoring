package p;

class C<E> {
}

interface I<T> {
	void m(C<T> c);
}

abstract class A implements I<Integer> {
	@Override
	public void m(C<Integer> c) {
		m(new C<Integer>());
	}
}
