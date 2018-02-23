package edu.cuny.hunter.streamrefactoring.core.analysis;

public class InstanceKeyNotFoundException extends Exception {

	private static final long serialVersionUID = 7562309214456762536L;

	public InstanceKeyNotFoundException() {
	}

	public InstanceKeyNotFoundException(String message) {
		super(message);
	}

	public InstanceKeyNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public InstanceKeyNotFoundException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public InstanceKeyNotFoundException(Throwable cause) {
		super(cause);
	}

}
