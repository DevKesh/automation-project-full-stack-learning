package com.project.qa.testsupport.web.faq;

import io.qameta.allure.*;
import org.openqa.selenium.*;
import org.slf4j.*;

import java.util.*;

/**
 * Interview Q36 — "How do you find broken images on a page with Selenium?"
 *
 * <p>This scanner implements the TWO viable techniques so the trade-off is explicit:
 *
 * <ol>
 *   <li><b>naturalWidth (browser-truth)</b> — {@link #findBrokenImages}. The browser has already
 *       attempted to load every {@code <img>}; we simply ask it the outcome. This is the
 *       professional default for images.</li>
 *   <li><b>HTTP status (the textbook 404/500 method)</b> — {@link #findBrokenImagesByHttpStatus}.
 *       Collect images by tag name, hit each {@code src}, and treat any {@code >= 400} as broken.
 *       Kept as a cross-check because it is the answer most interviewers expect to hear.</li>
 * </ol>
 *
 * <p><b>Why naturalWidth is preferred for images:</b> an HTTP 200 only proves bytes were served — it
 * does not prove the browser could decode them. A truncated/corrupt payload, a wrong MIME type, or a
 * CORS-blocked cross-origin image all return 200 yet render nothing. {@code naturalWidth == 0} on a
 * completed image is the browser's own verdict and catches every one of those cases.
 */
public class BrokenImageScanner {

	private static final Logger log = LoggerFactory.getLogger(BrokenImageScanner.class);

	// The browser's own load verdict for one element: "the load attempt finished AND pixels decoded".
	private static final String NATURAL_WIDTH_PROBE =
			"return arguments[0].complete && arguments[0].naturalWidth > 0;";

	/**
	 * Primary technique. Collects every {@code <img>} by tag name and asks the browser, per element,
	 * whether it actually rendered pixels.
	 *
	 * @return one {@link BrokenImage} per image that failed to render; empty if the page is clean.
	 */
	@Step("Scan page for broken images via the browser's naturalWidth")
	public List<BrokenImage> findBrokenImages(WebDriver driver) {
		List<WebElement> images = driver.findElements(By.tagName("img"));
		log.info("Scanning {} <img> element(s) using the naturalWidth technique", images.size());

		JavascriptExecutor js = (JavascriptExecutor) driver;
		List<BrokenImage> broken = new ArrayList<>();

		for (WebElement image : images) {
			String src = image.getAttribute("src");
			boolean rendered = Boolean.TRUE.equals(js.executeScript(NATURAL_WIDTH_PROBE, image));
			if (rendered) {
				log.debug("OK     -> {}", src);
			} else {
				log.debug("BROKEN -> {}", src);
				broken.add(new BrokenImage(src, "Browser reported naturalWidth == 0 (image never decoded)"));
			}
		}

		log.info("naturalWidth scan complete: {} broken of {} total", broken.size(), images.size());
		return broken;
	}

	/**
	 * Textbook technique. Collects every {@code <img>} by tag name and issues an HTTP request per
	 * {@code src}, treating any {@code 4xx}/{@code 5xx} (or a connection failure) as broken.
	 *
	 * @return one {@link BrokenImage} per image whose URL did not return a success status.
	 */
	@Step("Scan page for broken images via HTTP status codes")
	public List<BrokenImage> findBrokenImagesByHttpStatus(WebDriver driver) {
		List<WebElement> images = driver.findElements(By.tagName("img"));
		log.info("Scanning {} <img> element(s) using the HTTP-status technique", images.size());

		List<BrokenImage> broken = new ArrayList<>();

		for (WebElement image : images) {
			// Selenium resolves src to an absolute URL, so relative paths (e.g. "img/x.jpg") are
			// already usable here without manual base-URL stitching.
			String src = image.getAttribute("src");
			if (src == null || src.isBlank()) {
				broken.add(new BrokenImage("<no src>", "Element has an empty or missing src attribute"));
				continue;
			}

			int status = HttpStatus.of(src);
			if (status >= 400) {
				log.debug("BROKEN ({}) -> {}", status, src);
				broken.add(new BrokenImage(src, "HTTP " + status));
			} else {
				log.debug("OK     ({}) -> {}", status, src);
			}
		}

		log.info("HTTP scan complete: {} broken of {} total", broken.size(), images.size());
		return broken;
	}
}
