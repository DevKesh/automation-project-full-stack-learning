package com.project.qa.testsupport.listeners;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/*
 * Attaches RetryAnalyzer to every @Test at load time, so the retry policy is applied suite-wide
 * without annotating each method individually.
 *
 * WHY a transformer over per-@Test wiring: a single registration in each testng.xml keeps the policy
 * centralised and impossible to forget on a new test — the alternative (retryAnalyzer = ... on every
 * @Test) is boilerplate that silently drifts as tests are added.
 */
public class RetryTransformer implements IAnnotationTransformer {

	@Override
	public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
		annotation.setRetryAnalyzer(RetryAnalyzer.class);
	}
}
