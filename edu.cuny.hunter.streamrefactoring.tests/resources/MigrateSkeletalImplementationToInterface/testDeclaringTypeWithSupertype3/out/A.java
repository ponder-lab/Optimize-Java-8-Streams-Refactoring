package p;

interface I {
	default void m() {
		System.out.println("Hello");
	}
}

class B {
	public void m() {
		System.out.println("Goodbye");
	}
}

abstract class A extends B implements I {
}

class Main {
	public static void main(String[] args) {
		B b = new B();
		b.m(); // prints goodbye.
	}
}