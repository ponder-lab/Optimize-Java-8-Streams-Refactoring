package p;

interface I {
	void m();
}

class A {
	void m() {
		abstract class B implements I {
			public void m() {
			}
		}
	}
}
