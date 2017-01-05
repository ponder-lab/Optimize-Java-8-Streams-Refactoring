package p;

interface I {
	int f = 5;
	void m();
}

abstract class A implements I {
	int f;
	public void m() {
		System.out.println(f);
	}
}

class Main {
	public static void main(String[] args) {
		new A() {}.m(); //prints 0.
	}
}
