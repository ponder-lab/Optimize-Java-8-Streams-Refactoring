package p;

interface I {
	void m();
}

class B {
	public static void n() {
	}
}

abstract class A extends B implements I {
	
	@Override
	public void m() {
		B.n();
	}
}