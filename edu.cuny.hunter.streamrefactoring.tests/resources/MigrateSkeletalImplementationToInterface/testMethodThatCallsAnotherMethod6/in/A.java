package p;

interface I {
	void m();
}

public abstract class A implements I {
	
	class C {
		void n() {
		}
	}

	@Override
	public void m() {
		this.new C().n();
	}
}
