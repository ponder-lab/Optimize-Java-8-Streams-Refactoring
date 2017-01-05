package p;

interface I {
	void m();
}

abstract class A implements I {
	int f;
	
	@Override
	public void m() {
		f = 5;
	}
}