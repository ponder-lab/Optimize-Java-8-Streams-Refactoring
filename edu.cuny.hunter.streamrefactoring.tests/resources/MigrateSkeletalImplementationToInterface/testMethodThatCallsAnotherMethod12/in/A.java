package p;

interface I {
	void m();
}

public abstract class A implements I {
	
	class C {
	}

	@Override
	public void m() {
		new C();
	}
}
