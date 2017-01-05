package p;

import javax.annotation.Resource;

interface I {
	@Resource
	void m();
}

abstract class A implements I {
	@Deprecated
	public void m() {
	}
}