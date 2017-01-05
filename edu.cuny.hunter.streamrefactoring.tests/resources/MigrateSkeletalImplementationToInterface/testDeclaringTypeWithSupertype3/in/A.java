package p;

interface I {
	void m();
}

class B {
	public void m() {
		System.out.println("Goodbye");
	}
}

abstract class A extends B implements I {
	@Override
	public void m() {
		System.out.println("Hello");
	}
}

class Main {
	public static void main(String[] args) {
		B b = new B();
		b.m(); // prints goodbye.
	}
}