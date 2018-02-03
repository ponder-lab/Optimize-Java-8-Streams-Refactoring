package edu.cuny.hunter.streamrefactoring.eval.utils;

import java.io.IOException;
import java.io.PrintWriter;

public class TXTPrinter {

	PrintWriter printWriter;

	public TXTPrinter(PrintWriter printWriter) {
		this.printWriter = printWriter;
	}

	public void print(String entrypoint) throws IOException {
		printWriter.println(entrypoint);
	}

	public void close() throws IOException {
		printWriter.close();
	}
}
