package p;

interface I {
	default void m(@Deprecated int n) {
	}
}

abstract class A implements I {
}
