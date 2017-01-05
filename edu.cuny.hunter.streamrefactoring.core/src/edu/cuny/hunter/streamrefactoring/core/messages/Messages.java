/**
 * 
 */
package edu.cuny.hunter.streamrefactoring.core.messages;

import org.eclipse.osgi.util.NLS;

/**
 * @author raffi
 *
 */
public class Messages extends NLS {
	private static final String BUNDLE_NAME = "edu.cuny.hunter.streamrefactoring.core.messages.messages"; //$NON-NLS-1$

	public static String Name;
	public static String CategoryName;
	public static String CategoryDescription;
	public static String StreamsNotSpecified;
	public static String CheckingPreconditions;
	public static String CompilingSource;
	public static String CreatingChange;
	public static String CUContainsCompileErrors;
	public static String NoStreamsHavePassedThePreconditions;
	public static String NoStreamsToConvert;

	public static String MethodDoesNotExist;
	public static String PreconditionFailed;
	public static String RefactoringNotPossible;
	public static String WrongType;
	public static String CantChangeMethod;
	public static String NoConstructors;
	public static String NoAnnotations;
	public static String NoStaticMethods;
	public static String NoAbstractMethods;
	public static String NoMethodsInInterfaces;
	public static String NoMethodsInAnonymousTypes;
	public static String NoMethodsInEnums;
	public static String NoMethodsInLambdas;
	public static String NoMethodsInLocals;
	public static String NoMethodsInMemberTypes;
	public static String NoLambdaMethods;
	public static String MethodsOnlyInClasses;
	public static String NoMethodsInAnnotatedTypes;
	public static String NoMethodsInTypesWithFields;
	public static String NoMethodsInTypesWithInitializers;
	public static String NoMethodsInTypesWithMoreThanOneMethod;
	public static String NoMethodsInTypesWithTypeParameters;
	public static String NoMethodsInTypesWithType;
	public static String NoMethodsInTypesWithSuperType;
	public static String NoMethodsInTypesThatDontImplementInterfaces;
	public static String NoMethodsInTypesThatExtendMultipleInterfaces;
	public static String NoMethodsThatThrowExceptions;
	public static String NoMethodsInConcreteTypes;
	public static String NoMethodsInStaticTypes;
	public static String NoMethodsWithParameters;
	public static String IncompatibleMethodReturnTypes;
	public static String NoMethodsWithTypeParameters;
	public static String NoMethodsWithStatements;
	public static String NoMethodsInBinaryTypes;
	public static String NoMethodsInReadOnlyTypes;
	public static String NoMethodsInTypesWithNoCandidateTargetTypes;
	public static String NoMethodsInTypesWithMultipleCandidateTargetTypes;
	public static String NoNativeMethods;
	public static String NoMoreThanOneMethod;
	public static String IncompatibleLanguageConstruct;
	public static String DestinationTypeMustBePureInterface;
	public static String DestinationInterfaceMustOnlyDeclareTheMethodToMigrate;
	public static String NoDestinationInterface;
	public static String TargetMethodIsAlreadyDefault;
	public static String DestinationInterfaceHasAnnotations;
	public static String DestinationInterfaceIsNotTopLevel;
	public static String DestinationInterfaceDeclaresFields;
	public static String DestinationInterfaceExtendsInterface;
	public static String DestinationInterfaceDeclaresTypeParameters;
	public static String DestinationInterfaceDeclaresMemberTypes;
	public static String DestinationInterfaceDoesNotExist;
	public static String DestinationInterfaceNotWritable;
	public static String DestinationInterfaceIsMember;
	public static String DestinationInterfaceHierarchyContainsInvalidClass;
	public static String DestinationInterfaceHierarchyContainsInvalidInterfaces;
	public static String DestinationInterfaceHierarchyContainsSubtype;
	public static String DestinationInterfaceHierarchyContainsSuperInterface;
	public static String DestinationInterfaceHierarchyContainsSupertype;
	public static String DestinationInterfaceHasExtendingInterface;
	public static String DestinationInterfaceHasInvalidImplementingClass;
	public static String DeclaringTypeHierarchyContainsInvalidClass;
	public static String DeclaringTypeHierarchyContainsInvalidInterface;
	public static String DeclaringTypeContainsSubtype;
	public static String DeclaringTypeContainsInvalidSupertype;
	public static String DestinationInterfaceIsFunctional;
	public static String MethodContainsInconsistentParameterAnnotations;
	public static String NoSynchronizedMethods;
	public static String NoFinalMethods;
	public static String DestinationInterfaceIsStrictFP;
	public static String ExceptionTypeMismatch;
	public static String AnnotationMismatch;
	public static String AnnotationNameMismatch;
	public static String AnnotationValueMismatch;
	public static String NoMethodsInAnnotationTypes;
	public static String SourceMethodHasNoTargetMethod;
	public static String SourceMethodAccessesInstanceField;
	public static String DestinationProjectIncompatible;
	public static String SourceMethodImplementsMultipleMethods;
	public static String SourceMethodOverridesMethod;
	public static String TargetMethodHasMultipleSourceMethods;
	public static String MethodContainsCallToProtectedObjectMethod;
	public static String MethodContainsSuperReference;
	public static String MethodContainsTypeIncompatibleThisReference;
	public static String SourceMethodProvidesImplementationsForMultipleMethods;
	public static String MethodContainsIncompatibleParameterTypeParameters;
	public static String MethodContainsQualifiedThisExpression;
	public static String DestinationInterfaceIsDerived;
	public static String SourceMethodIsDerived;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
		super();
	}
}
