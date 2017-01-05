package p;

interface I {
	default strictfp void m() {
	}
}

abstract strictfp class A implements I {
}