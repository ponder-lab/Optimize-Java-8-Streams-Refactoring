package p;

interface I {
	default void m() {
		System.out.println("Hello");
	}
}

interface J extends I {
	void m();
}

abstract class A implements J {
}

class Main {
	public static void main(String[] args) {
		new A() {}.m();
	}
}
