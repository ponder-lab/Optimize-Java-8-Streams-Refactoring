/**
 * 
 */
package edu.cuny.hunter.streamrefactoring.ui.tests;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ui.tests.refactoring.Java18Setup;
import org.eclipse.ltk.core.refactoring.Refactoring;

import edu.cuny.hunter.streamrefactoring.core.utils.Util;
import edu.cuny.citytech.refactoring.common.tests.RefactoringTest;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author <a href="mailto:rkhatchadourian@citytech.cuny.edu">Raffi
 *         Khatchadourian</a>
 *
 */
@SuppressWarnings("restriction")
public class MigrateSkeletalImplementationToInterfaceRefactoringTest extends RefactoringTest {

	private static final Class<MigrateSkeletalImplementationToInterfaceRefactoringTest> clazz = MigrateSkeletalImplementationToInterfaceRefactoringTest.class;

	private static final Logger logger = Logger.getLogger(clazz.getName());

	private static final String REFACTORING_PATH = "MigrateSkeletalImplementationToInterface/";

	static {
		logger.setLevel(Level.FINER);
	}

	public static Test setUpTest(Test test) {
		return new Java18Setup(test);
	}

	public static Test suite() {
		return setUpTest(new TestSuite(clazz));
	}

	public MigrateSkeletalImplementationToInterfaceRefactoringTest(String name) {
		super(name);
	}

	@Override
	public String getRefactoringPath() {
		return REFACTORING_PATH;
	}

	@Override
	protected Refactoring getRefactoring(IMethod... methods) throws JavaModelException {
		return Util.createRefactoring(methods);
	}

	@Override
	protected Logger getLogger() {
		return logger;
	}

	private void helperFailLambdaMethod(String typeName, String lambdaExpression) throws Exception {
		ICompilationUnit cu = createCUfromTestFile(getPackageP(), typeName);
		IBuffer buffer = cu.getBuffer();
		String contents = buffer.getContents();
		int start = contents.indexOf(lambdaExpression);
		IJavaElement[] elements = cu.codeSelect(start, 1);

		assertEquals("Incorrect no of elements", 1, elements.length);
		IJavaElement element = elements[0];

		assertEquals("Incorrect element type", IJavaElement.LOCAL_VARIABLE, element.getElementType());

		IMethod method = (IMethod) element.getParent();
		assertFailedPrecondition(method);
	}

	public void testConstructor() throws Exception {
		helperFail(new String[] { "A" }, new String[][] { new String[0] });
	}

	public void testAnnotatedMethod() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testAnnotatedMethod2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testAnnotatedMethod3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testAnnotatedMethod4() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testAnnotatedMethod5() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testAnnotatedMethod6() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testAnnotatedMethod7() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testAbstractMethod() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testStaticMethod() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testFinalMethod() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	/**
	 * Synchronized methods aren't allowed in interfaces.
	 */
	public void testSynchronizedMethod() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testStrictFPMethod() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testStrictFPMethod2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testStrictFPMethod3() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testStrictFPMethod4() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testLambdaMethod() throws Exception {
		helperFailLambdaMethod("A", "x) -> {}");
	}

	public void testMethodContainedInInterface() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInAnonymousType() throws Exception {
		helperFail("m", new String[] {}, new String[] { "n" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInEnum() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInLocalType() throws Exception {
		helperFail("m", new String[] {}, "B", new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInMemberType() throws Exception {
		helperPass("B", new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInMemberType2() throws Exception {
		// qualified this expression refers to the outer class. Should fail as
		// there will be no such outer class after the refactoring.
		helperFail("B", new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInMemberType3() throws Exception {
		// qualified this expression refers to the inner (declaring) class.
		// Should fail as the inner class won't be available after the
		// refactoring.
		helperFail("B", new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInMemberType4() throws Exception {
		helperFail("B", new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInMemberType5() throws Exception {
		helperFail("B", new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInAnnotation() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInAnnotatedType() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField() throws Exception {
		// Just a simple field that's not accessed in the source method.
		// Should pass.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField2() throws Exception {
		// Here, we have a local variable with the same type and name as the
		// field. The refactoring should not be confused between the two. Should
		// pass.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField3() throws Exception {
		// Source method accesses a field of the declaring type. Should fail
		// because interfaces can't have fields.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField4() throws Exception {
		// Same as 3 but with private field. Should fail.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField5() throws Exception {
		// Same as 3 but with public field. Should fail.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField6() throws Exception {
		// Same as 3 but with protected field. Should fail.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField7() throws Exception {
		// Access a package-private static field. Should pass.
		// TODO: #92.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField8() throws Exception {
		// Same as 7 but public static field.
		// TODO: #93.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField9() throws Exception {
		// Same as 7 but protected static field. Should pass.
		// TODO: #94.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField10() throws Exception {
		// Same as 7 but private static field. Should fail.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField11() throws Exception {
		// Same as 7 but in an inner class. Should pass #95.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField12() throws Exception {
		// Same as 11 but a public field. Should pass #96.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField13() throws Exception {
		// Same as 11 but a protected field. Should pass #97.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField14() throws Exception {
		// Same as 11 but a private field. Should fail.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField15() throws Exception {
		// Same as 7 but in a static inner class. Should pass #98.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField16() throws Exception {
		// Same as 15 but a public field. Should pass #99.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField17() throws Exception {
		// Same as 15 but a protected field. Should pass #100.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithField18() throws Exception {
		// Same as 15 but a private field. Should fail.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithInitializer() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithMoreThanOneMethod() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithTypeParameters() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithTypeParameters2() throws Exception {
		// FIXME: This should fail. Blocked on: PullUp pulls up a method
		// referencing a type
		// variable that is not present in the super class
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=495874.
		helperPass(new String[] { "m" }, new String[][] { new String[0] }, false);
	}

	public void testMethodContainedInTypeWithTypeParameters3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithTypeParameters4() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithTypeParameters5() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeWithTypeParameters6() throws Exception {
		// Type parameter bound differences here. Should fail.
		// FIXME: This should fail. Blocked on: 495877: Pull Up ignores type
		// parameter bounds
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=495877.
		helperPass(new String[] { "m" }, new String[][] { new String[0] }, false);
	}

	public void testMethodContainedInTypeWithSuperTypes() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodContainedInTypeThatImplementsMultipleInterfaces() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInTypeThatImplementsInterfaceWithSuperInterfaces() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInTypeThatImplementsInterfaceWithSuperInterfaces2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatThrowsAnException() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatThrowsAnException2() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInConcreteType() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodDeclaredInStaticType() throws Exception {
		helperPass("B", new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithParameters() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithParameters2() throws Exception {
		// Should fail as the generics don't match up.
		helperFail(new String[] { "m" }, new String[][] { new String[] { "QC<QInteger;>;" } });
	}

	public void testMethodWithParameters3() throws Exception {
		// Should pass as we can substitute the type parameters.
		helperPass(new String[] { "m" }, new String[][] { new String[] { "QC<QE;>;" } });
	}

	public void testMethodWithAnnotatedParameters() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParameters2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParameters3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT, Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParameters4() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParameters5() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParameters6() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParameters7() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT, Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParameters8() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParameters9() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParameters10() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	/**
	 * Mismatched annotated parameters.
	 */
	public void testMethodWithAnnotatedParametersWithConflicts() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithAnnotatedParametersWithConflicts2() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithReturnType() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithReturnType2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithReturnType3() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithReturnType4() throws Exception {
		// should fail because the generics don't match up.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithReturnType5() throws Exception {
		// should fail because the generics don't match up.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithReturnType6() throws Exception {
		// should pass because we can substitute type parameters.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithTypeParameters() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithStatements() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testNoMethods() throws Exception {
		helperFail();
	}

	public void testMultipleMethods() throws Exception {
		helperFail(new String[] { "m", "n" }, new String[][] { new String[0], new String[0] });
	}

	public void testMultipleMethods2() throws Exception {
		helperPass(new String[] { "m", "n" }, new String[][] { new String[0], new String[0] });
	}

	public void testMultipleMethods3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMultipleMethods4() throws Exception {
		helperPass(new String[] { "m", "n" }, new String[][] { new String[0], new String[0] });
	}

	public void testMultipleMethods5() throws Exception {
		// Two eligible methods here but only migrate one of them.
		helperPass(new String[] { "m" }, new String[][] { new String[0], new String[0] });
	}

	public void testMultipleMethods6() throws Exception {
		// Two eligible methods here but only migrate one of them. Also, the one
		// being
		// migrated calls the one not being migrated.
		helperPass(new String[] { "m" }, new String[][] { new String[0], new String[0] });
	}

	public void testMultipleMethods7() throws Exception {
		// Same as 6 but no method definition in the abstract class.
		helperPass(new String[] { "m" }, new String[][] { new String[0], new String[0] });
	}

	// TODO: Also need to test when the run-time type could be different.

	public void testTargetInterfaceWithMultipleMethods() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testTargetInterfaceWithNoMethods() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testPureTargetInterface() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testTargetInterfaceWithNoTargetMethods() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDefaultTargetMethod() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithAnnotations() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testNonTopLevelDestinationInterface() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testNonTopLevelDestinationInterface2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithFields() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithFields2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithFields3() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithFields4() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithFields5() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithFields6() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithFields7() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	/**
	 * The destination interface should not be marked as an @FunctionalInterface
	 * since we only convert abstract methods to default methods and it is not
	 * allowed for a valid @FunctionalInterface to loose an abstract method.
	 */
	public void testDestinationFunctionalInterface() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	/**
	 * Same as
	 * {@link MigrateSkeletalImplementationToInterfaceRefactoringTest#testDestinationFunctionalInterface()}
	 * but with non-abstract methods also included in the interface.
	 */
	public void testDestinationFunctionalInterfaceWithNonAbstractMethods() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	/**
	 * Same as
	 * {@link MigrateSkeletalImplementationToInterfaceRefactoringTest#testDestinationFunctionalInterface()}
	 * but with non-abstract methods also included in the interface.
	 */
	public void testDestinationFunctionalInterfaceWithNonAbstractMethods2() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceThatExtendsInterface() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithTypeParameters() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithTypeParameters2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithTypeParameters3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithTypeParameters4() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithMemberTypes() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMemberDestinationInterface() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMemberDestinationInterface2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidClass() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidClass2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidClass3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidClass4() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidClass5() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidInterface() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidInterface2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidInterface3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidInterface4() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidInterface5() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithInvalidInterface6() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMultipleDestinationInterfaces() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMultipleDestinationInterfaces2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMultipleDestinationInterfaces3() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithSubtype() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceHierarchyWithSuperInterface() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDestinationInterfaceWithExtendingInterface() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidClass() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidClass2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidClass3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidClass4() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidClass5() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidClass6() throws Exception {
		// here, we're testing a case where the called method is in both
		// of the declaring type and destination interface hierarchies.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidClass7() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidClass8() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidInterface() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeHierarchyWithInvalidInterface3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype3() throws Exception {
		// Duplicate default methods named m with the parameters () and () are
		// inherited from the types I and J #159.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype4() throws Exception {
		// The type C must implement the inherited abstract method J.m() #159.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype5() throws Exception {
		// Cannot directly invoke the abstract method m() for the type J #159.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype6() throws Exception {
		// This should pass as the immediate derived class overrides the
		// migrated method #159.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype7() throws Exception {
		// This should pass as there's already a default method but semantics
		// are preserved thanks to "class-wins" #159.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype8() throws Exception {
		// Covariant return type test for #159. This should pass as the
		// subclass overrides the migrated method from the declaring class.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype9() throws Exception {
		// Another Duplicate default methods named m with the parameters () and
		// () are
		// inherited from the types I and J. In this case, we have the
		// satisfying method definition in a subclass, which should still fail
		// #159.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype10() throws Exception {
		// Like above but the satisfying method definition is in the top-level
		// class. Should pass #159.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSubtype11() throws Exception {
		// Class down the hierarchy implements two interfaces. Should fail #159.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSupertype() throws Exception {
		// this test has a field in the super type but the method to migrate
		// does not access it.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSupertype2() throws Exception {
		// like the first but with access.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSupertype3() throws Exception {
		// here, class wins but there isn't a call to A.m(), thus preserving
		// semantics. Still, I feel like we should fail here because we would
		// changing the method relationships.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSupertype4() throws Exception {
		// here, class wins and there is a call to A.m(), thus preserving
		// semantics.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithSupertype5() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testDeclaringTypeWithInvalidSupertype() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithNoTargetMethod() throws Exception {
		// this source method has no target. It should fail.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod() throws Exception {
		// this source method calls another method in the interface. It should
		// pass.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod2() throws Exception {
		// this source method calls another method outside the interface. It
		// should fail.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod3() throws Exception {
		// this source method calls another method outside both the interface
		// and the class. The method is accessible from both locations. It
		// should pass.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod4() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod5() throws Exception {
		// TODO: This should fail because the implicit parameter of the
		// constructor call is actually `this` due to the inner class #162.
		helperPass(new String[] { "m" }, new String[][] { new String[0] }, false);
	}

	public void testMethodThatCallsAnotherMethod6() throws Exception {
		// TODO: Should fail #162.
		helperPass(new String[] { "m" }, new String[][] { new String[0] }, false);
	}

	public void testMethodThatCallsAnotherMethod7() throws Exception {
		// this should pass #78.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod8() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod9() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod10() throws Exception {
		// in this example, the source method calls a method declared in the
		// target interface but defined in the source method's declaring type.
		// The run time target of the method call remains intact. Related to
		// #77.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod11() throws Exception {
		// in this example, the source method calls a method defined both in the
		// target interface and the source method's declaring type.
		// The run time target of the method call remains intact because the
		// source method's declaring type overrides the default method in the
		// target interface. Related to #77.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod12() throws Exception {
		// like testMethodThatCallsAnotherMethod5 but call ctor instead of a
		// method.
		// TODO: Should fail #162.
		helperPass(new String[] { "m" }, new String[][] { new String[0] }, false);
	}

	public void testMethodThatCallsAnotherMethod13() throws Exception {
		// Source method calls System.out.println(). Should pass.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsAnotherMethod14() throws Exception {
		// Source method calls a static method. Should pass.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsConstructor() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatCallsConstructor2() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatUsesThis() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatUsesThis2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatUsesThis3() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatUsesThis4() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatUsesThis5() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatUsesThis6() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatUsesThis7() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatUsesThis8() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[] { Signature.createTypeSignature("A", false) } });
	}

	public void testMethodThatUsesThis9() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[] { Signature.createTypeSignature("I", false) } });
	}

	public void testMethodThatUsesThis10() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatUsesThis11() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesType() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesType2() throws Exception {
		// This test should pass #77.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesType3() throws Exception {
		// This test should fail #77.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesType4() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesType5() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesType6() throws Exception {
		// Should pass #77.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesType7() throws Exception {
		// Generics don't match up here.
		// FIXME: This should fail. Blocked on: PullUp pulls up a method
		// referencing a type
		// variable that is not present in the super class
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=495874.
		helperPass(new String[] { "m" }, new String[][] { new String[0] }, false);
	}

	public void testMethodThatAccessesType8() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithMultiplePossibleTargets() throws Exception {
		// TODO: Need to figure out how to resolve ambiguous targets.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithInheritedDefaultMethod() throws Exception {
		/*
		 * Suppose that a skeletal implementation A defines a method m() and
		 * implements two interfaces I and J, each of which declare the same
		 * method m(). As such, A.m() is an implementation of both I.m() and
		 * J.m(). Further suppose that I.m() is a default method. In this case,
		 * A.m() overrides I.m(). If we choose to migrate A.m() to J as a
		 * default method, then any subclass of A inheriting A.m() will break
		 * because it must now choose which implementation, either I.m() or
		 * J.m(), it will inherit.
		 */
		// TODO: Correctly failing, yes, but for the wrong reason. We aren't
		// checking the hierarchy. #114.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithInheritedDefaultMethod2() throws Exception {
		// Same as above but reverse the interfaces.
		// TODO: Correctly failing, yes, but for the wrong reason. We aren't
		// checking the hierarchy. #114.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithInheritedDefaultMethod3() throws Exception {
		// Similar to above but with a more complex hierarchy. This one should
		// pass because the problem is in a sub-interface.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithInheritedDefaultMethod4() throws Exception {
		// Similar to above but with a more complex hierarchy. This one should
		// fail because the problem is in a super-interface.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithNoInheritedDefaultMethod() throws Exception {
		// Should pass because, although multiple interfaces are implemented,
		// there is no conflict.
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatSkipsType() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatSkipsType2() throws Exception {
		// Like above but this one implement interface directly.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatSkipsType3() throws Exception {
		// Here, B doesn't explicitly implement the interface but we still have
		// the
		// same problem.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatSkipsType4() throws Exception {
		// This one should fail due to type checking.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatDoesntSkipType() throws Exception {
		// "control"
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatDoesntSkipType2() throws Exception {
		// "control"
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testComplicatedHierarchy() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithTargetThatHasMultipleSourceMethods() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] }, new String[] { "m" },
				new String[][] { new String[0] });
	}

	public void testMethodWithTargetThatHasMultipleSourceMethods2() throws Exception {
		helperPassNoFatal(new String[] { "m" }, new String[][] { new String[0] }, new String[] { "m" },
				new String[][] { new String[0] });
	}

	public void testMethodWithTargetThatHasMultipleSourceMethods3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] }, new String[] { "m" },
				new String[][] { new String[0] });
	}

	public void testMethodWithTargetThatHasMultipleSourceMethods4() throws Exception {
		helperPassNoFatal(new String[] { "m" }, new String[][] { new String[0] }, new String[] { "m" },
				new String[][] { new String[0] });
	}

	public void testMethodWithTargetThatHasMultipleSourceMethods5() throws Exception {
		helperPassNoFatal(new String[] { "m" }, new String[][] { new String[0] }, new String[] { "m" },
				new String[][] { new String[0] }, new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithTargetThatHasMultipleSourceMethods6() throws Exception {
		helperPassNoFatal(new String[] { "m" }, new String[][] { new String[0] }, new String[] { "m" },
				new String[][] { new String[0] }, new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithCallsToObjectMethods() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithCallsToObjectMethods2() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithCallsToObjectMethods3() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithCallsToObjectMethods4() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithSuperReference() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithSuperReference2() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodWithAbstractTargetMethod() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfADifferentType() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfADifferentType2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceType() throws Exception {
		// control of the above.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceType2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceType3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceType4() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceType5() throws Exception {
		// control of the above.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceType6() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceType7() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceType8() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceTypeHierarchy() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceTypeHierarchy2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceTypeHierarchy3() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceTypeHierarchy4() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceTypeHierarchy5() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceFieldOfTheSourceTypeHierarchy6() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceMethodOfADifferentType() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceMethodOfTheSourceType() throws Exception {
		// control of the above.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceMethodOfTheSourceType2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceMethodOfTheSourceType3() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceMethodOfTheSourceType4() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceMethodOfTheSourceType5() throws Exception {
		// This should fail per #149.
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceMethodOfTheSourceTypeHierarchy() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceMethodOfTheSourceTypeHierarchy2() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testMethodThatAccessesPublicInstanceMethodOfTheSourceTypeHierarchy3() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testSkeletalImplementerHierarchy() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testSkeletalImplementerHierarchy2() throws Exception {
		helperPassNoFatal(new String[] { "m" }, new String[][] { new String[0] }, new String[] { "m" },
				new String[][] { new String[0] });
	}

	public void testSkeletalImplementerHierarchy3() throws Exception {
		helperFail(new String[] { "m" }, new String[][] { new String[0] });
	}

	public void testSkeletalImplementerHierarchy4() throws Exception {
		helperPassNoFatal(new String[] { "m" }, new String[][] { new String[0] }, new String[] { "m" },
				new String[][] { new String[0] });
	}

	public void testMethodThatHasDifferentParameterNamesThanTarget() throws Exception {
		helperPass(new String[] { "m" }, new String[][] { new String[] { Signature.SIG_INT } });
	}

	public void testMethodWithTargetInLibrary() throws Exception {
		helperFail(new String[] { "showStatus" },
				new String[][] { new String[] { Signature.createTypeSignature("String", false) } });
	}
}