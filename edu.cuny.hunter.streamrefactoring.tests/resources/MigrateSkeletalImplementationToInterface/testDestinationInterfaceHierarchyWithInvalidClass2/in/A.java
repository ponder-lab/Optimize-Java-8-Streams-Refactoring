package p;

interface I {
	void m();
}

class B {
}

abstract class A extends B implements I {
	public void m() {
	}
}