package p;

interface I {
}

interface J extends I {
	void m();
}

abstract class B implements J {
}

abstract class A extends B {
	public void m() {
	}
}
