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

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceManipulation;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.ui.tests.refactoring.Java18Setup;
import org.eclipse.jdt.ui.tests.refactoring.RefactoringTest;

import edu.cuny.hunter.streamrefactoring.core.analysis.ExecutionMode;
import edu.cuny.hunter.streamrefactoring.core.analysis.Ordering;
import edu.cuny.hunter.streamrefactoring.core.analysis.Stream;
import edu.cuny.hunter.streamrefactoring.core.analysis.StreamAnalysisVisitor;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author <a href="mailto:raffi.khatchadourian@hunter.cuny.edu">Raffi
 *         Khatchadourian</a>
 *
 */
@SuppressWarnings("restriction")
public class ConvertStreamToParallelRefactoringTest extends RefactoringTest {

	/**
	 * The name of the directory containing resources under the project
	 * directory.
	 */
	private static final String RESOURCE_PATH = "resources";

	private static final Class<ConvertStreamToParallelRefactoringTest> clazz = ConvertStreamToParallelRefactoringTest.class;

	private static final Logger logger = Logger.getLogger(clazz.getName());

	private static final String REFACTORING_PATH = "ConvertStreamToParallel/";

	private static final int MAX_RETRY = 5;

	private static final int RETRY_DELAY = 1000;

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

		// full path of where the CU exists.
		Path directory = Paths.get(unit.getParent().getParent().getParent().getResource().getLocation().toString());

		// compile it to make and store the class file.
		compiles(unit.getSource(), directory);

			return unit;
	}

	@Override
	protected ICompilationUnit createCUfromTestFile(IPackageFragment pack, String cuName, boolean input)
			throws Exception {
		String contents = input ? getFileContents(getInputTestFileName(cuName))
				: getFileContents(getOutputTestFileName(cuName));
		return createCU(pack, cuName + ".java", contents);
	}

	public static ICompilationUnit createCU(IPackageFragment pack, String name, String contents) throws Exception {
		ICompilationUnit compilationUnit = pack.getCompilationUnit(name);

		for (int i = 0; i < MAX_RETRY; i++) {
			boolean exists = compilationUnit.exists();

			if (exists) {
				if (i == MAX_RETRY - 1)
					assertFalse("Compilation unit: " + compilationUnit.getElementName() + " exists.", exists);
				else {
					logger.info("Sleeping.");
					Thread.sleep(RETRY_DELAY);
				}

			} else
				break;
		}

		ICompilationUnit cu = pack.createCompilationUnit(name, contents, true, null);
		cu.save(null, true);
		return cu;
	}

	protected Logger getLogger() {
		return logger;
	}

	private static boolean compiles(String source, Path directory) throws IOException {
		// Save source in .java file.
		File sourceFile = new File(directory.toFile(), "bin/p/A.java");
		sourceFile.getParentFile().mkdirs();
		Files.write(sourceFile.toPath(), source.getBytes());

		// Compile source file.
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		boolean compileSuccess = compiler.run(null, null, null, sourceFile.getPath()) == 0;

		sourceFile.delete();
		return compileSuccess;
	}
	
	private void refreshFromLocal() throws CoreException {
		if (getRoot().exists())
			getRoot().getResource().refreshLocal(IResource.DEPTH_INFINITE, null);
		else if (getPackageP().exists())// don't refresh package if root already
										// refreshed
			getPackageP().getResource().refreshLocal(IResource.DEPTH_INFINITE, null);
	}

	@Override
	protected void tearDown() throws Exception {
		refreshFromLocal();
		performDummySearch();

		final boolean pExists = getPackageP().exists();

		if (pExists)
			tryDeletingAllJavaClassFiles(getPackageP());

		Stream.clearCaches();
		super.tearDown();
	}

	private static void tryDeletingAllJavaClassFiles(IPackageFragment pack) throws JavaModelException {
		IJavaElement[] kids = pack.getChildren();
		for (int i = 0; i < kids.length; i++) {
			if (kids[i] instanceof ISourceManipulation) {
				if (kids[i].exists() && !kids[i].isReadOnly()) {
					IPath path = kids[i].getPath();

					// change the file extension.
					path = path.removeFileExtension();
					path = path.addFileExtension("class");

					// change src to bin.
					// get the root directory.
					IPath root = path.uptoSegment(1);

					// append bin to it.
					IPath bin = root.append("bin");

					// get the package and class part.
					IPath packageAndClass = path.removeFirstSegments(2);

					// append it to the bin directory.
					path = bin.append(packageAndClass);

					// path it relative, so must construct absolute.
					// get the test workspace
					IPath testWorkspace = pack.getParent().getParent().getParent().getResource().getLocation();

					// append the test directory to the test workspace.
					path = testWorkspace.append(path);

					// convert the path to a file.
					File classFile = path.toFile();

					// delete the file.
					try {
						Files.delete(classFile.toPath());
					} catch (IOException e) {
						throw new IllegalArgumentException(
								"Class file for " + kids[i].getElementName() + " does not exist.", e);
					}
				}
			}
		}
	}

	private static boolean compiles(String source) throws IOException {	
		return compiles(source,  Files.createTempDirectory(null));
	}

	public void testArraysAsList() throws Exception {
		helper("Arrays.asList().stream()", ExecutionMode.SEQUENTIAL, Ordering.ORDERED);
	}

	public void testHashSetParallelStream() throws Exception {
		helper("new HashSet<>().parallelStream()", ExecutionMode.PARALLEL, Ordering.UNORDERED);
	}

	public void testArraysStream() throws Exception {
		helper("Arrays.stream(new Object[1])", ExecutionMode.SEQUENTIAL, Ordering.ORDERED);
	}

	public void testBitSet() throws Exception {
		helper("set.stream()", ExecutionMode.SEQUENTIAL, Ordering.ORDERED);
	}

	public void testIntermediateOperations() throws Exception {
		helper("set.stream()", ExecutionMode.SEQUENTIAL, Ordering.ORDERED);
	}

	public void testGenerate() throws Exception {
		helper("Stream.generate(() -> 1)", ExecutionMode.SEQUENTIAL, Ordering.UNORDERED);
	}

	public void testTypeResolution() throws Exception {
		helper("anotherSet.parallelStream()", ExecutionMode.PARALLEL, Ordering.UNORDERED);
	}

	private void helper(String expectedCreation, ExecutionMode expectedExecutionMode, Ordering expectedOrdering)
			throws Exception {
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

		Set<ExecutionMode> executionModes = stream.getPossibleExecutionModes();
		assertTrue(executionModes.size() == 1);
		assertTrue(executionModes.contains(expectedExecutionMode));

		Set<Ordering> orderings = stream.getPossibleOrderings();
		assertTrue(orderings.size() == 1);
		assertTrue(orderings.contains(expectedOrdering));
	}
}
