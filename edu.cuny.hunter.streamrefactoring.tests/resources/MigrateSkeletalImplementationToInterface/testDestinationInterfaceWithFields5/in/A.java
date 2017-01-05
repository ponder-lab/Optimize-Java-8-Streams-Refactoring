package p;

interface I {
	int f = 5;

	void m();
}

abstract class A implements I {
	public void m() {
		int f = 0;
		System.out.println(f);
	}
}

class Main {
	public static void main(String[] args) {
		new A() {
		}.m(); // prints 0.
	}
}
