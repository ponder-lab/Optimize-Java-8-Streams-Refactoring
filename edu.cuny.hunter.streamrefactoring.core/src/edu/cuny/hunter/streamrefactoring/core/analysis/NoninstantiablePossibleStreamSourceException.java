package edu.cuny.hunter.streamrefactoring.core.analysis;

public class NoninstantiablePossibleStreamSourceException extends Exception {

	private static final long serialVersionUID = 2364202699579315467L;

	public NoninstantiablePossibleStreamSourceException(String string) {
		super(string);
	}

	public NoninstantiablePossibleStreamSourceException(String string, Throwable cause) {
		super(string, cause);
	}

}
