package p;

import java.io.IOException;

interface I {
	void m() throws Exception;
}

abstract class A implements I {
	public void m() throws IOException {
	}
}