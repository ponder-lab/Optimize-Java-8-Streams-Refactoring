package edu.cuny.hunter.streamrefactoring.core.analysis;

public class UnhandledCaseException extends Exception {

	private static final long serialVersionUID = -8906594098077250054L;

	public UnhandledCaseException() {
	}

	public UnhandledCaseException(String message) {
		super(message);
	}

	public UnhandledCaseException(String message, Throwable cause) {
		super(message, cause);
	}

	public UnhandledCaseException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public UnhandledCaseException(Throwable cause) {
		super(cause);
	}
}
