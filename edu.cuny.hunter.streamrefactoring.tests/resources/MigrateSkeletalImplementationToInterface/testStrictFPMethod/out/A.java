package p;

interface I {
	default strictfp void m() {
	}
}

abstract class A implements I {
}