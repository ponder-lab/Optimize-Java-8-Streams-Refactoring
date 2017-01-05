package p;

interface I<E> {
	void m();
}

abstract class A<E extends java.util.AbstractList<String>> implements I<E> {
	@Override
	public void m() {
		E e = null;
		e.add("test");
	}
}
