package p;

interface I<E> {
	default void m() {
		E e = null;
		e.add("test");
	}
}

abstract class A<E extends java.util.AbstractList<String>> implements I<E> {
}
