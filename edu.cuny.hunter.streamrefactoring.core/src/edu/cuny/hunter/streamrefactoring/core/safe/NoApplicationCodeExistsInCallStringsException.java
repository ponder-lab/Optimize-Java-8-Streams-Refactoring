package edu.cuny.hunter.streamrefactoring.core.safe;

public class NoApplicationCodeExistsInCallStringsException extends Exception {

	public NoApplicationCodeExistsInCallStringsException(String message) {
		super(message);
	}

	public NoApplicationCodeExistsInCallStringsException(Throwable cause) {
		super(cause);
	}

	public NoApplicationCodeExistsInCallStringsException(String message, Throwable cause) {
		super(message, cause);
	}

	public NoApplicationCodeExistsInCallStringsException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
