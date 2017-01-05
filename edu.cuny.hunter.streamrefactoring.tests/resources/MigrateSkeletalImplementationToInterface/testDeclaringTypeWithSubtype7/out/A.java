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

public abstract class A implements I {
}

abstract class C extends A implements J {
	@Override
	public void m() {
		super.m();
	}
}

class B extends C {
}

class Main {
	public static void main(String[] args) {
		// Should print hello.
		new B().m();
	}
}