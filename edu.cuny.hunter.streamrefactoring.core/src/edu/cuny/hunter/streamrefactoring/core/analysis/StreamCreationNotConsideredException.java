package edu.cuny.hunter.streamrefactoring.core.analysis;

public class StreamCreationNotConsideredException extends Exception {

	private static final long serialVersionUID = 8177203523095969738L;

	public StreamCreationNotConsideredException() {
	}

	public StreamCreationNotConsideredException(String message) {
		super(message);
	}

	public StreamCreationNotConsideredException(Throwable cause) {
		super(cause);
	}

	public StreamCreationNotConsideredException(String message, Throwable cause) {
		super(message, cause);
	}

	public StreamCreationNotConsideredException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
