package p;

interface I {
	void m();
}

abstract class B implements I {
}

abstract class A extends B {
	public void m() {
	}
}