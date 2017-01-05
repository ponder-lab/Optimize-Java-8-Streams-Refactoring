package p;

interface I {
	default void m() {
		System.out.println("Hello");
	}
}

interface J extends I {
	default void m() {
		System.out.println("Goodbye");
	}
}

abstract class A implements J {
}

class Main {
	public static void main(String[] args) {
		new A() {}.m();
	}
}
