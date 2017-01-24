package edu.cuny.hunter.streamrefactoring.core.analysis;

public class CannotExtractSpliteratorException extends Exception {

	private static final long serialVersionUID = 5599211330376425600L;

	public CannotExtractSpliteratorException() {
	}

	public CannotExtractSpliteratorException(String message) {
		super(message);
	}

	public CannotExtractSpliteratorException(Throwable cause) {
		super(cause);
	}

	public CannotExtractSpliteratorException(String message, Throwable cause) {
		super(message, cause);
	}

	public CannotExtractSpliteratorException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
