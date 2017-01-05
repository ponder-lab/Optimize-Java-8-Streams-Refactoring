package p;

interface I {
	int f = 5;
	default void m() {
		System.out.println(f);
	}
}

abstract class A implements I {
	int f;
}

class Main {
	public static void main(String[] args) {
		new A() {}.m(); //prints 5.
	}
} 