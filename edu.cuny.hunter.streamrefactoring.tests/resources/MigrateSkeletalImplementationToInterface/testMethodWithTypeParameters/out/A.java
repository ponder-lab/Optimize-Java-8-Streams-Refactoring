package p;

interface I {
	default <T> void m() {
	}
}

abstract class A implements I {
}
