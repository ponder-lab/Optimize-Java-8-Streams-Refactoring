package p;

interface I {
	int f = 0;
	void m();
}

abstract class A implements I {
	public void m() {
		System.out.println(f);
	}
}
