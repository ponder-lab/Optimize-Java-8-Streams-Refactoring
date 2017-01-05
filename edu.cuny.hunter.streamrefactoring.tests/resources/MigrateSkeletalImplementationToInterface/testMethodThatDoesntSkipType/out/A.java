package p;

interface I {
	default void m() {
		System.out.println("Hello");
	}
}

abstract class A implements I {
}

class Main {
	public static void main(String[] args) {
		new A() {}.m(); //should print Hello.
	}
}