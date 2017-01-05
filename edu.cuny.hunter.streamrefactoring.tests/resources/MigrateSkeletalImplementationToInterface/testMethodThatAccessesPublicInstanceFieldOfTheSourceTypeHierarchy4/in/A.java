package p;

interface I {
	void m();
}

class B {
	int f;
}

abstract class A extends B implements I {
	
	@Override
	public void m() {
		f = 5;
	}
}