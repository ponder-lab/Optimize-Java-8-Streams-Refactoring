package p;

interface I {
	void m();
}

interface J extends I {
	void m();
}

abstract class A implements J {
	public void m() {
		System.out.println("Hello");
	}
}

class Main {
	public static void main(String[] args) {
		new A() {}.m();
	}
}
