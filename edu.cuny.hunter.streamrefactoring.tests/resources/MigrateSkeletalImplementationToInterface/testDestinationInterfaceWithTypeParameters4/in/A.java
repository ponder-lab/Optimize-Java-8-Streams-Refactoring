package p;

import java.util.AbstractCollection;

interface I<T extends AbstractCollection<T>> {
	void m();
}

abstract class A<E extends AbstractCollection<E>> implements I<E> {
	@Override
	public void m() {
		E e = null;
	}
}
