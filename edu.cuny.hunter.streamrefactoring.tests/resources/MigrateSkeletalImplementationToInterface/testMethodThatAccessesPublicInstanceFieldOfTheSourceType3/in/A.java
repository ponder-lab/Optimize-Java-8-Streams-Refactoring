package p;

interface I {
	void m();
}

class A implements I {
	int f;

	@Override
	public void m() {
		int g = new A().f;
	}
}