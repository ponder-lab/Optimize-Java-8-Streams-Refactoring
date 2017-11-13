package edu.cuny.hunter.streamrefactoring.core.analysis;

public class CannotInferOrderingViaReflectionException extends Exception {

	private static final long serialVersionUID = 1985378036156482987L;

	public CannotInferOrderingViaReflectionException(String message, ClassNotFoundException e) {
		super(message, e);
	}

}
