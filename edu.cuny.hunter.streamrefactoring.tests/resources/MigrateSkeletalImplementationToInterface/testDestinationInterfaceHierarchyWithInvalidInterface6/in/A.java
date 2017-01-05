package p;

interface I {
	void m();
}

interface J extends I {
	default void m() {
		System.out.println("Goodbye");
	}
}

abstract class A implements I {
	public void m() {
		System.out.println("Hello");
	}
}

abstract class B implements J {
}

class Main {
	public static void main(String[] args) {
		new B() {}.m();
	}
}
