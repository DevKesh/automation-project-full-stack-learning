package com.project.qa.pages;

import com.project.qa.core.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import org.slf4j.*;

import java.time.*;

public class BasePage {
	protected WebDriverWait wait;

	public BasePage() {
		// We initialize a 10-second explicit wait tied to the thread-safe driver
		this.wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(10));
	}

	protected void click(By locator) {

		wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
	}

	protected void type(By locator, String text) {
		WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		element.clear();
		element.sendKeys(text);
	}

	protected String getText(By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator)).getText();
	}
}
