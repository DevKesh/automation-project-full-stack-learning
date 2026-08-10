package com.project.qa.pages;

import com.project.qa.config.*;
import com.project.qa.core.*;
import io.qameta.allure.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import org.slf4j.*;

import java.time.*;

public class BasePage {
	protected WebDriverWait wait;

	public BasePage() {
		// Explicit-wait ceiling is config-driven (ConfigReader), so timeouts are tuned per environment
		// without touching code. Bound to this thread's driver, keeping parallel runs isolated.
		this.wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(ConfigReader.getExplicitWaitSeconds()));
	}

	// @Step instruments the Allure timeline at the single point every UI action funnels through, so
	// each page/DSL action is reported without annotating every page object. The locator is included
	// deliberately: until a semantic-key layer (StepTranslator) sits on top, the resolved By is the
	// only element identifier available, and it makes a failing step actionable in the report.
	@Step("Click {locator}")
	protected void click(By locator) {

		wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
	}

	@Step("Type \"{text}\" into {locator}")
	protected void type(By locator, String text) {
		WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		element.clear();
		element.sendKeys(text);
	}

	@Step("Read text from {locator}")
	protected String getText(By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator)).getText();
	}
}
