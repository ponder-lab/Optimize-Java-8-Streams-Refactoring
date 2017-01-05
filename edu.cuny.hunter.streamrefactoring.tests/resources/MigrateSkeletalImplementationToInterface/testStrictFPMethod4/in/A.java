package p;

strictfp interface I {
	void m();
}

abstract class A implements I {
	public strictfp void m() {
	}
}