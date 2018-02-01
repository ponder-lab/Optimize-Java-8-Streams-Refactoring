package edu.cuny.hunter.streamrefactoring.eval.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.ibm.wala.ipa.callgraph.Entrypoint;

public class TXTPrinter {

	FileWriter fileWriter;

	public TXTPrinter(File file) throws IOException {
		if (!file.getParentFile().exists())
			file.getParentFile().mkdirs();
		this.fileWriter = new FileWriter(file);
	}

	public void print(final Object value) {

	}

	public void print(Entrypoint entrypoint) throws IOException {
		fileWriter.write(entrypoint.getMethod().getSignature());
		fileWriter.write(System.lineSeparator());
	}

	public void close() throws IOException {
		fileWriter.close();
	}
}
