package p;

interface I {
	default void m() throws CloneNotSupportedException {
		this.clone();
	}
}

abstract class A implements I {
}