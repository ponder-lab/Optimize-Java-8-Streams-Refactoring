package p;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class A {
	static {
		Map map = new HashMap();
		map.entrySet().stream().count();
	}

	public static void main(String[] args) {
	}
}