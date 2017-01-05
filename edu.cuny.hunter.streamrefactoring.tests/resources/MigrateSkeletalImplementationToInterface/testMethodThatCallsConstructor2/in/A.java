package p;

interface I {
	void m();
}

public class A implements I {
	
	private A() {
	}

	@Override
	public void m() {
		new A();
	}
}
