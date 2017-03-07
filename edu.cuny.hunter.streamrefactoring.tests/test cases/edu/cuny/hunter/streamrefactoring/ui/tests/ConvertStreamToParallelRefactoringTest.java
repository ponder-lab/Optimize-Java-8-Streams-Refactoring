/**
 * 
 */
package edu.cuny.hunter.streamrefactoring.ui.tests;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.ui.tests.refactoring.Java18Setup;

import edu.cuny.hunter.streamrefactoring.core.analysis.Stream;
import edu.cuny.hunter.streamrefactoring.core.analysis.StreamAnalysisVisitor;
import edu.cuny.hunter.streamrefactoring.core.analysis.StreamExecutionMode;
import edu.cuny.hunter.streamrefactoring.core.analysis.StreamOrdering;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi
 *         Khatchadourian</a>
 *
 */
@SuppressWarnings("restriction")
public class ConvertStreamToParallelRefactoringTest extends org.eclipse.jdt.ui.tests.refactoring.RefactoringTest {

	/**
	 * The name of the directory containing resources under the project
	 * directory.
	 */
	private static final String RESOURCE_PATH = "resources";

	private static final Class<ConvertStreamToParallelRefactoringTest> clazz = ConvertStreamToParallelRefactoringTest.class;

	private static final Logger logger = Logger.getLogger(clazz.getName());

	private static final String REFACTORING_PATH = "ConvertStreamToParallel/";

	static {
		logger.setLevel(Level.FINER);
	}

	public static Test setUpTest(Test test) {
		return new Java18Setup(test);
	}

	public static Test suite() {
		return setUpTest(new TestSuite(clazz));
	}

	public ConvertStreamToParallelRefactoringTest(String name) {
		super(name);
	}

	@Override
	public String getRefactoringPath() {
		return REFACTORING_PATH;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.jdt.ui.tests.refactoring.RefactoringTest#getFileContents(java
	 * .lang.String) Had to override this method because, since this plug-in is
	 * a fragment (at least I think that this is the reason), it doesn't have an
	 * activator and the bundle is resolving to the eclipse refactoring test
	 * bundle.
	 */
	@Override
	public String getFileContents(String fileName) throws IOException {
		Path absolutePath = getAbsolutionPath(fileName);
		byte[] encoded = Files.readAllBytes(absolutePath);
		return new String(encoded, Charset.defaultCharset());
	}

	private Path getAbsolutionPath(String fileName) {
		Path path = Paths.get(RESOURCE_PATH, fileName);
		Path absolutePath = path.toAbsolutePath();
		return absolutePath;
	}

	public void setFileContents(String fileName, String contents) throws IOException {
		Path absolutePath = getAbsolutionPath(fileName);
		Files.write(absolutePath, contents.getBytes());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.jdt.ui.tests.refactoring.RefactoringTest#createCUfromTestFile
	 * (org.eclipse.jdt.core.IPackageFragment, java.lang.String)
	 */
	@Override
	protected ICompilationUnit createCUfromTestFile(IPackageFragment pack, String cuName) throws Exception {
		ICompilationUnit unit = super.createCUfromTestFile(pack, cuName);

		if (!unit.isStructureKnown())
			throw new IllegalArgumentException(cuName + " has structural errors.");
		else
			return unit;
	}

	protected Logger getLogger() {
		return logger;
	}

	private static boolean compiles(String source) throws IOException {
		// Save source in .java file.
		Path root = Files.createTempDirectory(null);
		File sourceFile = new File(root.toFile(), "p/A.java");
		sourceFile.getParentFile().mkdirs();
		Files.write(sourceFile.toPath(), source.getBytes());

		// Compile source file.
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		return compiler.run(null, null, null, sourceFile.getPath()) == 0;
	}

	public void testArraysAsList() throws Exception {
		helper("Arrays.asList().stream()", StreamExecutionMode.SEQUENTIAL, StreamOrdering.ORDERED);
	}

	public void testHashSetParallelStream() throws Exception {
		helper("new HashSet<>().parallelStream()", StreamExecutionMode.PARALLEL, StreamOrdering.UNORDERED);
	}

	public void testArraysStream() throws Exception {
		helper("Arrays.stream(new Object[1])", StreamExecutionMode.SEQUENTIAL, StreamOrdering.ORDERED);
	}

	public void testBitSet() throws Exception {
		helper("set.stream()", StreamExecutionMode.SEQUENTIAL, StreamOrdering.ORDERED);
	}

	public void testIntermediateOperations() throws Exception {
		helper("set.stream()", StreamExecutionMode.SEQUENTIAL, StreamOrdering.ORDERED);
	}

	public void testGenerate() throws Exception {
		helper("Stream.generate(() -> 1)", StreamExecutionMode.SEQUENTIAL, StreamOrdering.UNORDERED);
	}

	public void testTypeResolution() throws Exception {
		helper("anotherSet.parallelStream()", StreamExecutionMode.PARALLEL, StreamOrdering.UNORDERED);
	}

	private void helper(String expectedCreation, StreamExecutionMode expectedExecutionMode,
			StreamOrdering expectedOrdering) throws Exception {
		ICompilationUnit cu = createCUfromTestFile(getPackageP(), "A");
		assertTrue("Input should compile.", compiles(cu.getSource()));

		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setResolveBindings(true);
		parser.setSource(cu);

		ASTNode ast = parser.createAST(new NullProgressMonitor());

		StreamAnalysisVisitor visitor = new StreamAnalysisVisitor();
		ast.accept(visitor);

		Set<Stream> streamSet = visitor.getStreamSet();

		assertNotNull(streamSet);
		assertEquals(1, streamSet.size());

		Stream stream = streamSet.iterator().next();

		MethodInvocation creation = stream.getCreation();
		assertEquals(expectedCreation, creation.toString());

		StreamExecutionMode executionMode = stream.getInitialExecutionMode();
		assertEquals(expectedExecutionMode, executionMode);

		StreamOrdering ordering = stream.getInitialOrdering();
		assertEquals(expectedOrdering, ordering);
	}
}