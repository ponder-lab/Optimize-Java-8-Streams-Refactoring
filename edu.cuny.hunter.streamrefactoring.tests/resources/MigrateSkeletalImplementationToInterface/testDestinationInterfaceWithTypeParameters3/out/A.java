package p;

interface I<T> {
	default void m() {
		T t = null;
	}
}

abstract class A<T> implements I<T> {
}
