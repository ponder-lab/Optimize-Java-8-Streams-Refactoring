package p;

interface I {
	default void m() throws CloneNotSupportedException {
		clone();
	}
}

abstract class A implements I {
}