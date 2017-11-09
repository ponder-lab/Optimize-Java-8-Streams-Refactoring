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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

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
import org.eclipse.jdt.ui.tests.refactoring.Java18Setup;
import org.eclipse.jdt.ui.tests.refactoring.RefactoringTest;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import edu.cuny.hunter.streamrefactoring.core.analysis.ExecutionMode;
import edu.cuny.hunter.streamrefactoring.core.analysis.Ordering;
import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionFailure;
import edu.cuny.hunter.streamrefactoring.core.analysis.PreconditionSuccess;
import edu.cuny.hunter.streamrefactoring.core.analysis.Refactoring;
import edu.cuny.hunter.streamrefactoring.core.analysis.Stream;
import edu.cuny.hunter.streamrefactoring.core.analysis.StreamAnalysisVisitor;
import edu.cuny.hunter.streamrefactoring.core.analysis.TransformationAction;
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
	 * The name of the directory containing resources under the project directory.
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

	private static boolean compiles(String source) throws IOException {
		return compiles(source, Files.createTempDirectory(null));
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

	public static ICompilationUnit createCU(IPackageFragment pack, String name, String contents) throws Exception {
		ICompilationUnit compilationUnit = pack.getCompilationUnit(name);

		for (int i = 0; i < MAX_RETRY; i++) {
			boolean exists = compilationUnit.exists();

			if (exists) {
				if (i == MAX_RETRY - 1)
					logger.warning("Compilation unit: " + compilationUnit.getElementName() + " exists.");
				else {
					logger.info("Sleeping.");
					Thread.sleep(RETRY_DELAY * i);
				}

			} else
				break;
		}

		ICompilationUnit cu = pack.createCompilationUnit(name, contents, true, null);
		cu.save(null, true);
		return cu;
	}

	public static Test setUpTest(Test test) {
		return new Java18Setup(test);
	}

	public static Test suite() {
		return setUpTest(new TestSuite(clazz));
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

	public ConvertStreamToParallelRefactoringTest(String name) {
		super(name);
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

	private Path getAbsolutionPath(String fileName) {
		Path path = Paths.get(RESOURCE_PATH, fileName);
		Path absolutePath = path.toAbsolutePath();
		return absolutePath;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.eclipse.jdt.ui.tests.refactoring.RefactoringTest#getFileContents(java
	 * .lang.String) Had to override this method because, since this plug-in is a
	 * fragment (at least I think that this is the reason), it doesn't have an
	 * activator and the bundle is resolving to the eclipse refactoring test bundle.
	 */
	@Override
	public String getFileContents(String fileName) throws IOException {
		Path absolutePath = getAbsolutionPath(fileName);
		byte[] encoded = Files.readAllBytes(absolutePath);
		return new String(encoded, Charset.defaultCharset());
	}

	protected Logger getLogger() {
		return logger;
	}

	@Override
	public String getRefactoringPath() {
		return REFACTORING_PATH;
	}

	/**
	 * Runs a single analysis test.
	 */
	private void helper(StreamAnalysisExpectedResult... expectedResults) throws Exception {
		// compute the actual results.
		ICompilationUnit cu = createCUfromTestFile(getPackageP(), "A");
		assertTrue("Input should compile.", compiles(cu.getSource()));

		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setResolveBindings(true);
		parser.setSource(cu);

		ASTNode ast = parser.createAST(new NullProgressMonitor());

		StreamAnalysisVisitor visitor = new StreamAnalysisVisitor();
		ast.accept(visitor);

		Set<Stream> resultingStreams = visitor.getStreamSet();
		assertNotNull(resultingStreams);

		Map<String, List<Stream>> creationStringToStreams = resultingStreams.stream()
				.collect(Collectors.groupingBy(s -> s.getCreation().toString()));

		// compare them with the expected results.
		// for each expected result.
		for (StreamAnalysisExpectedResult result : expectedResults) {
			// find the corresponding stream in the actual results.
			List<Stream> expectingStreams = creationStringToStreams.get(result.getExpectedCreation());

			String errorMessage = "Can't find corresponding stream for creation: " + result.getExpectedCreation();
			assertNotNull(errorMessage, expectingStreams);
			assertFalse(errorMessage, expectingStreams.isEmpty());

			assertEquals("Ambigious corresponding stream for creation: " + result.getExpectedCreation(), 1,
					expectingStreams.size());

			Stream stream = expectingStreams.get(0);

			Set<ExecutionMode> executionModes = stream.getPossibleExecutionModes();
			assertEquals(result.getExpectedExecutionModes(), executionModes);

			Set<Ordering> orderings = stream.getPossibleOrderings();
			assertEquals(result.getExpectedOrderings(), orderings);

			assertEquals(result.isExpectingSideEffects(), stream.hasPossibleSideEffects());
			assertEquals(result.isExpectingStatefulIntermediateOperation(),
					stream.hasPossibleStatefulIntermediateOperations());
			assertEquals(result.isExpectingThatReduceOrderingMatters(), stream.reduceOrderingPossiblyMatters());
			assertEquals(result.getExpectedActions(), stream.getActions());
			assertEquals(result.getExpectedPassingPrecondition(), stream.getPassingPrecondition());
			assertEquals(result.getExpectedRefactoring(), stream.getRefactoring());
			assertEquals(result.getExpectedStatusSeverity(), stream.getStatus().getSeverity());

			Set<Integer> actualCodes = Arrays.stream(stream.getStatus().getEntries()).map(e -> e.getCode())
					.collect(Collectors.toSet());

			Set<Integer> expectedCodes = result.getExpectedFailures().stream().map(e -> e.getCode())
					.collect(Collectors.toSet());

			assertEquals(expectedCodes, actualCodes);
		}
	}

	private void refreshFromLocal() throws CoreException {
		if (getRoot().exists())
			getRoot().getResource().refreshLocal(IResource.DEPTH_INFINITE, null);
		else if (getPackageP().exists())// don't refresh package if root already
										// refreshed
			getPackageP().getResource().refreshLocal(IResource.DEPTH_INFINITE, null);
	}

	public void setFileContents(String fileName, String contents) throws IOException {
		Path absolutePath = getAbsolutionPath(fileName);
		Files.write(absolutePath, contents.getBytes());
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

	/**
	 * Fix https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/34.
	 *
	 * @throws Exception
	 */
	public void testArraysAsList() throws Exception {
		boolean passed = false;
		try {
			helper(new StreamAnalysisExpectedResult("Arrays.asList().stream()",
					Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.ORDERED), false,
					false, false, null, null, null, RefactoringStatus.ERROR,
					Collections.singleton(PreconditionFailure.NO_TERMINAL_OPERATIONS)));
		} catch (NullPointerException e) {
			logger.throwing(this.getClass().getName(), "testArraysAsList", e);
			passed = true;
		}
		assertTrue("Should fail per #34", passed);
	}

	/**
	 * Fix https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/80.
	 *
	 * @throws Exception
	 */
	public void testArraysStream() throws Exception {
		boolean passed = false;
		try {
			helper(new StreamAnalysisExpectedResult("Arrays.stream(new Object[1])",
					Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.ORDERED), false,
					false, false, null, null, null, RefactoringStatus.ERROR,
					Collections.singleton(PreconditionFailure.NO_TERMINAL_OPERATIONS)));
		} catch (IllegalArgumentException e) {
			logger.throwing(this.getClass().getName(), "testArraysAsStream", e);
			passed = true;
		}
		assertTrue("Should fail per #80", passed);
	}

	public void testBitSet() throws Exception {
		helper(new StreamAnalysisExpectedResult("set.stream()", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.ORDERED), false, false, false,
				Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P2,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));
	}

	/**
	 * Fix https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/80.
	 *
	 * @throws Exception
	 */
	public void testGenerate() throws Exception {
		helper(new StreamAnalysisExpectedResult("Stream.generate(() -> 1)",
				Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.UNORDERED), false,
				false, false, Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P1,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));

	}

	public void testIntStreamGenerate() throws Exception {
		helper(new StreamAnalysisExpectedResult("IntStream.generate(() -> 1)",
				Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.ORDERED), false, false,
				false, Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P2,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));

	}

	public void testStreamOf() throws Exception {
		helper(new StreamAnalysisExpectedResult("Stream.of(\"a\")", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.ORDERED), false, false, false,
				Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P2,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));

	}

	public void testIntStreamOf() throws Exception {
		helper(new StreamAnalysisExpectedResult("IntStream.of(1)", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.ORDERED), false, false, false,
				Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P2,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));

	}

	public void testLongStreamOf() throws Exception {
		helper(new StreamAnalysisExpectedResult("LongStream.of(1111)", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.ORDERED), false, false, false,
				Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P2,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));

	}

	public void testDoubleStreamOf() throws Exception {
		helper(new StreamAnalysisExpectedResult("DoubleStream.of(1.111)",
				Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.ORDERED), false, false,
				false, Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P2,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));

	}

	public void testHashSetParallelStream() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().parallelStream()",
				Collections.singleton(ExecutionMode.PARALLEL), Collections.singleton(Ordering.UNORDERED), false, false,
				false, null, null, null, RefactoringStatus.ERROR,
				Collections.singleton(PreconditionFailure.NO_TERMINAL_OPERATIONS)));
	}

	public void testHashSetParallelStream2() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().parallelStream()",
				Collections.singleton(ExecutionMode.PARALLEL), Collections.singleton(Ordering.UNORDERED), false, true,
				false, null, null, null, RefactoringStatus.ERROR, EnumSet.of(PreconditionFailure.UNORDERED)));
	}

	public void testStaticInitializer() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().parallelStream()", Collections.singleton(null),
				Collections.singleton(null), false, false, false, null, null, null, RefactoringStatus.ERROR,
				EnumSet.of(PreconditionFailure.CURRENTLY_NOT_HANDLED)));
	}

	public void testIntermediateOperations() throws Exception {
		helper(new StreamAnalysisExpectedResult("set.stream()", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.ORDERED), false, true, false,
				EnumSet.of(TransformationAction.UNORDER, TransformationAction.CONVERT_TO_PARALLEL),
				PreconditionSuccess.P3, Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK,
				Collections.emptySet()));
	}

	public void testTypeResolution() throws Exception {
		helper(new StreamAnalysisExpectedResult("anotherSet.parallelStream()",
				Collections.singleton(ExecutionMode.PARALLEL), Collections.singleton(Ordering.UNORDERED), false, false,
				false, null, null, null, RefactoringStatus.ERROR,
				Collections.singleton(PreconditionFailure.NO_TERMINAL_OPERATIONS)));
	}

	public void testTypeResolution2() throws Exception {
		helper(new StreamAnalysisExpectedResult("anotherSet.parallelStream()",
				Collections.singleton(ExecutionMode.PARALLEL), Collections.singleton(Ordering.UNORDERED), false, false,
				false, null, null, null, RefactoringStatus.ERROR,
				Collections.singleton(PreconditionFailure.UNORDERED)));
	}

	public void testMotivatingExample() throws Exception {
		helper(new StreamAnalysisExpectedResult("unorderedWidgets.stream()", EnumSet.of(ExecutionMode.SEQUENTIAL),
				EnumSet.of(Ordering.ORDERED), false, false, true, EnumSet.of(TransformationAction.CONVERT_TO_PARALLEL),
				PreconditionSuccess.P2, Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK,
				Collections.emptySet()),

				new StreamAnalysisExpectedResult("orderedWidgets.parallelStream()", EnumSet.of(ExecutionMode.PARALLEL),
						EnumSet.of(Ordering.ORDERED), false, false, false, null, null, null, RefactoringStatus.ERROR,
						EnumSet.of(PreconditionFailure.NO_STATEFUL_INTERMEDIATE_OPERATIONS)),

				new StreamAnalysisExpectedResult("orderedWidgets.stream()", EnumSet.of(ExecutionMode.SEQUENTIAL),
						EnumSet.of(Ordering.ORDERED), false, true, true, null, null, null, RefactoringStatus.ERROR,
						EnumSet.of(PreconditionFailure.REDUCE_ORDERING_MATTERS)));
	}

	public void testTerminalOp1() throws Exception {
		helper(new StreamAnalysisExpectedResult("collection1.stream()", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.UNORDERED), false, false, false,
				Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P1,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()),

				new StreamAnalysisExpectedResult("collection2.stream()",
						Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.UNORDERED),
						false, false, false, null, null, null, RefactoringStatus.ERROR,
						Collections.singleton(PreconditionFailure.NO_TERMINAL_OPERATIONS)));
	}

	public void testTerminalOp2() throws Exception {
		helper(new StreamAnalysisExpectedResult("collection1.stream()", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.UNORDERED), false, false, false, null, null, null,
				RefactoringStatus.ERROR, Collections.singleton(PreconditionFailure.NO_TERMINAL_OPERATIONS)),

				new StreamAnalysisExpectedResult("collection2.stream()",
						Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.UNORDERED),
						false, false, false, Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL),
						PreconditionSuccess.P1, Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK,
						Collections.emptySet()));
	}

	public void testTerminalOp3() throws Exception {
		helper(new StreamAnalysisExpectedResult("collection1.stream()", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.UNORDERED), false, false, false, null, null, null,
				RefactoringStatus.ERROR, Collections.singleton(PreconditionFailure.NO_TERMINAL_OPERATIONS)),

				new StreamAnalysisExpectedResult("collection2.stream()",
						Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.UNORDERED),
						false, false, false, null, null, null, RefactoringStatus.ERROR,
						Collections.singleton(PreconditionFailure.NO_TERMINAL_OPERATIONS)));
	}
}