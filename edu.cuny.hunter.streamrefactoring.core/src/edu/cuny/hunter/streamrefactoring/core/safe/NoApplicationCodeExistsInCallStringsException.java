package edu.cuny.hunter.streamrefactoring.core.safe;

public class NoApplicationCodeExistsInCallStringsException extends Exception {

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public NoApplicationCodeExistsInCallStringsException(String message) {
		super(message);
	}

	public NoApplicationCodeExistsInCallStringsException(String message, Throwable cause) {
		super(message, cause);
	}

	public NoApplicationCodeExistsInCallStringsException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public NoApplicationCodeExistsInCallStringsException(Throwable cause) {
		super(cause);
	}
}
