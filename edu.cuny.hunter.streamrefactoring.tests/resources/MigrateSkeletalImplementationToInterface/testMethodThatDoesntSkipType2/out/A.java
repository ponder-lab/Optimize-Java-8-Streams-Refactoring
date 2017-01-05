package p;

interface I {
	default void m() {
		System.out.println("Hello");
	}
}

abstract class B {
}

abstract class A extends B implements I {
}

class Main {
	public static void main(String[] args) {
		new A() {}.m(); //should print Hello.
	}
}