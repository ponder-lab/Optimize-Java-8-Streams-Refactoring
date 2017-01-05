package p;

interface I {
	void n();
}

class A {
	void m() {
		new I() {
			public void n() {
			}
		};
	}
}
