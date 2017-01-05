package p;

import javax.annotation.Generated;

interface I {
	default void m(@Generated("hello") @Deprecated int n) {
	}
}

abstract class A implements I {
}