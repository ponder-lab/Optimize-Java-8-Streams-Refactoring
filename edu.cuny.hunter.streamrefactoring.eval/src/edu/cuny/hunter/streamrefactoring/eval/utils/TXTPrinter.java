package edu.cuny.hunter.streamrefactoring.eval.utils;

import java.io.FileWriter;
import java.io.IOException;

public class TXTPrinter {

	FileWriter fileWriter;

	public TXTPrinter(FileWriter fileWriter) {
		this.fileWriter = fileWriter;
	}

	public void print(final Object value) {

	}

	public void print(String entrypoint) throws IOException {
		fileWriter.write(entrypoint);
		fileWriter.write(System.lineSeparator());
	}

	public void close() throws IOException {
		fileWriter.close();
	}
}
