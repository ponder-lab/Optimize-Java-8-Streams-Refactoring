package p;

interface I {
	@Deprecated
	default
	void m() {
	}
}

abstract class A implements I {
}