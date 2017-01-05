package p;

interface I {
	void m();
}

abstract class A implements I {
	public void m() {
		System.out.println("Hello");
	}
}
