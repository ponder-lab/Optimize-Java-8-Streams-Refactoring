package p;

import java.util.ArrayList;

import edu.cuny.hunter.streamrefactoring.annotations.EntryPoint;

class A {
	@EntryPoint
	void m() {
		new A() {
			@Override
			void m() {
				new ArrayList().stream().count();
			}
		};
	}
}
