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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
	 * The name of the directory containing resources under the project
	 * directory.
	 */
	private static final String RESOURCE_PATH = "resources";

	private static final Class<ConvertStreamToParallelRefactoringTest> CLAZZ = ConvertStreamToParallelRefactoringTest.class;

	private static final Logger LOGGER = Logger.getLogger(CLAZZ.getName());

	private static final String REFACTORING_PATH = "ConvertStreamToParallel/";

	private static final int MAX_RETRY = 5;

	private static final int RETRY_DELAY = 1000;

	static {
		LOGGER.setLevel(Level.FINER);
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

		boolean compileSuccess = compiler.run(null, null, null, "-classpath",
				System.getProperty("user.dir") + File.separator + "resources" + File.separator
						+ "ConvertStreamToParallel" + File.separator + "lib" + File.separator
						+ "stream-refactoring-annotations.jar",
				sourceFile.getPath()) == 0;

		sourceFile.delete();
		return compileSuccess;
	}

	public static ICompilationUnit createCU(IPackageFragment pack, String name, String contents) throws Exception {
		ICompilationUnit compilationUnit = pack.getCompilationUnit(name);

		for (int i = 0; i < MAX_RETRY; i++) {
			boolean exists = compilationUnit.exists();

			if (exists) {
				if (i == MAX_RETRY - 1)
					LOGGER.warning("Compilation unit: " + compilationUnit.getElementName() + " exists.");
				else {
					LOGGER.info("Sleeping.");
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
		return setUpTest(new TestSuite(CLAZZ));
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
		assertTrue("Input should compile", compiles(unit.getSource(), directory));

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

	protected Logger getLogger() {
		return LOGGER;
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
			assertEquals(errorMessage("execution mode", result), result.getExpectedExecutionModes(), executionModes);

			Set<Ordering> orderings = stream.getPossibleOrderings();
			assertEquals(errorMessage("orderings", result), result.getExpectedOrderings(), orderings);

			assertEquals(errorMessage("side effects", result), result.isExpectingSideEffects(),
					stream.hasPossibleSideEffects());
			assertEquals(errorMessage("stateful intermediate operations", result),
					result.isExpectingStatefulIntermediateOperation(),
					stream.hasPossibleStatefulIntermediateOperations());
			assertEquals(errorMessage("ROM", result), result.isExpectingThatReduceOrderingMatters(),
					stream.reduceOrderingPossiblyMatters());
			assertEquals(errorMessage("transformation actions", result), result.getExpectedActions(),
					stream.getActions());
			assertEquals(errorMessage("passing precondition", result), result.getExpectedPassingPrecondition(),
					stream.getPassingPrecondition());
			assertEquals(errorMessage("refactoring", result), result.getExpectedRefactoring(), stream.getRefactoring());
			assertEquals(errorMessage("status severity", result), result.getExpectedStatusSeverity(),
					stream.getStatus().getSeverity());

			Set<Integer> actualCodes = Arrays.stream(stream.getStatus().getEntries()).map(e -> e.getCode())
					.collect(Collectors.toSet());

			Set<Integer> expectedCodes = result.getExpectedFailures().stream().map(e -> e.getCode())
					.collect(Collectors.toSet());

			assertEquals(errorMessage("status codes", result), expectedCodes, actualCodes);
		}
	}

	private static String errorMessage(String attribute, StreamAnalysisExpectedResult result) {
		return "Unexpected " + attribute + " for " + result.getExpectedCreation() + ".";
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
		helper(new StreamAnalysisExpectedResult("Arrays.asList().stream()",
				Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.ORDERED), false, false,
				false, null, null, null, RefactoringStatus.ERROR,
				Collections.singleton(PreconditionFailure.NO_TERMINAL_OPERATIONS)));
	}

	/**
	 * Fix https://github.com/ponder-lab/Java-8-Stream-Refactoring/issues/80.
	 *
	 * @throws Exception
	 */
	public void testArraysStream() throws Exception {
		// Should fail per #80.
		helper(new StreamAnalysisExpectedResult("Arrays.stream(new Object[1])",
				Collections.singleton(ExecutionMode.SEQUENTIAL), null, false, false, false, null, null, null,
				RefactoringStatus.ERROR, Collections.singleton(PreconditionFailure.CURRENTLY_NOT_HANDLED)));
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

	public void testMultipleCallsToEnclosingMethod() throws Exception {
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

	/**
	 * This should change once #103 is fixed.
	 */
	public void testNonInternalAPI() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().parallelStream()",
				Collections.singleton(ExecutionMode.PARALLEL), Collections.singleton(Ordering.UNORDERED), false, false,
				false, null, null, null, RefactoringStatus.ERROR, EnumSet.of(PreconditionFailure.UNORDERED)));
	}

	public void testNonInternalAPI2() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().stream()",
				Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.UNORDERED), false, true,
				false, Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P1,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));
	}

	public void testNonInternalAPI3() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().stream()",
				Collections.singleton(ExecutionMode.SEQUENTIAL), Collections.singleton(Ordering.UNORDERED), false, true,
				false, Collections.singleton(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P1,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));
	}

	/**
	 * related to #126
	 */
	public void testNonInternalAPI4() throws Exception {
		HashSet<Ordering> orderings = new HashSet<>();
		orderings.add(Ordering.UNORDERED);
		orderings.add(Ordering.ORDERED);

		helper(new StreamAnalysisExpectedResult("new HashSet<>().stream()",
				Collections.singleton(ExecutionMode.SEQUENTIAL), orderings, false, true, false, null, null, null,
				RefactoringStatus.ERROR, EnumSet.of(PreconditionFailure.INCONSISTENT_POSSIBLE_ORDERINGS)));
	}

	/**
	 * related to #126
	 */
	public void testNonInternalAPI5() throws Exception {
		HashSet<ExecutionMode> executionModes = new HashSet<>();
		executionModes.add(ExecutionMode.PARALLEL);
		executionModes.add(ExecutionMode.SEQUENTIAL);
		helper(new StreamAnalysisExpectedResult("new HashSet<>().stream()", executionModes,
				Collections.singleton(Ordering.UNORDERED), false, true, false, null, null, null,
				RefactoringStatus.ERROR, EnumSet.of(PreconditionFailure.INCONSISTENT_POSSIBLE_EXECUTION_MODES)));
	}

	/**
	 * related to #126
	 */
	public void testNonInternalAPI6() throws Exception {
		HashSet<ExecutionMode> executionModes = new HashSet<>();
		executionModes.add(ExecutionMode.PARALLEL);
		executionModes.add(ExecutionMode.SEQUENTIAL);
		helper(new StreamAnalysisExpectedResult("new HashSet<>().stream()", executionModes,
				Collections.singleton(Ordering.UNORDERED), false, true, false, null, null, null,
				RefactoringStatus.ERROR, EnumSet.of(PreconditionFailure.INCONSISTENT_POSSIBLE_EXECUTION_MODES)));
	}

	/**
	 * This is a control to testNonInternalAPI4.
	 */
	public void testNonInternalAPI7() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().stream()",
				Collections.singleton(ExecutionMode.SEQUENTIAL), EnumSet.of(Ordering.ORDERED), false, true, false,
				EnumSet.of(TransformationAction.UNORDER, TransformationAction.CONVERT_TO_PARALLEL),
				PreconditionSuccess.P3, Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK,
				Collections.emptySet()));
	}

	public void testCollectionFromParameter() throws Exception {
		helper(new StreamAnalysisExpectedResult("h.parallelStream()", Collections.singleton(ExecutionMode.PARALLEL),
				Collections.singleton(Ordering.UNORDERED), false, true, false, null, null, null,
				RefactoringStatus.ERROR, EnumSet.of(PreconditionFailure.UNORDERED)));
	}

	public void testCollectionFromParameter2() throws Exception {
		helper(new StreamAnalysisExpectedResult("h.parallelStream()", Collections.singleton(ExecutionMode.PARALLEL),
				Collections.singleton(Ordering.UNORDERED), false, true, false, null, null, null,
				RefactoringStatus.ERROR, EnumSet.of(PreconditionFailure.UNORDERED)));
	}

	/**
	 * Test for #98.
	 */
	public void testCollectionFromParameter3() throws Exception {
		helper(new StreamAnalysisExpectedResult("h.parallelStream()", Collections.singleton(ExecutionMode.PARALLEL),
				Collections.singleton(Ordering.UNORDERED), false, true, false, null, null, null,
				RefactoringStatus.ERROR, EnumSet.of(PreconditionFailure.UNORDERED)));
	}

	/**
	 * Test for #98. Ordering.ORDERED because we are falling back.
	 */
	public void testCollectionFromParameter4() throws Exception {
		helper(new StreamAnalysisExpectedResult("h.parallelStream()", Collections.singleton(ExecutionMode.PARALLEL),
				Collections.singleton(Ordering.ORDERED), false, false, false, null, null, null, RefactoringStatus.ERROR,
				EnumSet.of(PreconditionFailure.CURRENTLY_NOT_HANDLED)));
	}

	public void testStaticInitializer() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().parallelStream()", null, null, false, false, false,
				null, null, null, RefactoringStatus.ERROR, EnumSet.of(PreconditionFailure.CURRENTLY_NOT_HANDLED)));
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
	
	public void testMultipleEntryPoints() throws Exception {
		helper(new StreamAnalysisExpectedResult("h1.stream()", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.UNORDERED), false, false, false,
				EnumSet.of(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P1,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));
	}

	public void testMultipleEntryPoints1() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().parallelStream()",
				Collections.singleton(ExecutionMode.PARALLEL), EnumSet.of(Ordering.UNORDERED), false, false, false,
				null, null, null, RefactoringStatus.ERROR, Collections.singleton(PreconditionFailure.UNORDERED)));
	}
	
	/**
	 * remove an annotation from testMultipleEntryPoints
	 */
	public void testOneEntryPoint() throws Exception {
		helper(new StreamAnalysisExpectedResult("h2.stream()", Collections.singleton(ExecutionMode.SEQUENTIAL),
				Collections.singleton(Ordering.UNORDERED), false, false, false,
				EnumSet.of(TransformationAction.CONVERT_TO_PARALLEL), PreconditionSuccess.P1,
				Refactoring.CONVERT_SEQUENTIAL_STREAM_TO_PARALLEL, RefactoringStatus.OK, Collections.emptySet()));
	}

	/**
	 * remove an annotation from testMultipleEntryPoints
	 */
	public void testOneEntryPoint1() throws Exception {
		helper(new StreamAnalysisExpectedResult("new HashSet<>().parallelStream()",
				Collections.singleton(ExecutionMode.PARALLEL), EnumSet.of(Ordering.UNORDERED), false, false, false,
				null, null, null, RefactoringStatus.ERROR, Collections.singleton(PreconditionFailure.UNORDERED)));
	}
}
