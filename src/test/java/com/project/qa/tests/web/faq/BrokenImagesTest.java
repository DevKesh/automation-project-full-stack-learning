package com.project.qa.tests.web.faq;

import com.project.qa.testsupport.constants.*;
import com.project.qa.framework.webdriver.*;
import com.project.qa.framework.mobiledriver.*;
import com.project.qa.testsupport.base.*;
import com.project.qa.testsupport.web.faq.*;
import io.qameta.allure.*;
import org.slf4j.*;
import org.testng.*;
import org.testng.annotations.*;

import java.util.*;

/**
 * Interview Q36 — "Explain how you can find broken images on a page using Selenium WebDriver."
 *
 * <p>The answer usually quoted (collect elements by tag name, then look for a 404/500 per URL) is the
 * HTTP-status recipe — and it is actually the recipe for broken <b>links</b>. For <b>images</b> the
 * browser already knows the truth, so this test leads with the {@code naturalWidth} technique and
 * then cross-checks it against the HTTP method to prove both agree.
 *
 * <p><b>Fixture:</b> {@code the-internet.herokuapp.com/broken_images} — the canonical Selenium
 * practice page. It serves 4 {@code <img>}s: two valid assets (the "Fork me" banner and an avatar)
 * and two dummy filenames ({@code asdf.jpg}, {@code hjkl.jpg}) that return 404. A controlled fixture
 * is what lets us assert an exact broken count instead of a vague "> 0".
 */
public class BrokenImagesTest extends BaseTest {

	private static final Logger log = LoggerFactory.getLogger(BrokenImagesTest.class);
	private static final String DEMO_URL = "https://the-internet.herokuapp.com/broken_images";
	private static final int EXPECTED_BROKEN = 2;

	private final BrokenImageScanner scanner = new BrokenImageScanner();

	@Test(groups = TestGroups.WEB)
	@Description("Finds broken images via the browser's naturalWidth and independently verifies with HTTP status codes.")
	public void detectsBrokenImagesOnPage() {
		DriverManager.getDriver().get(DEMO_URL);

		List<BrokenImage> brokenByBrowser = scanner.findBrokenImages(DriverManager.getDriver());
		List<BrokenImage> brokenByHttp = scanner.findBrokenImagesByHttpStatus(DriverManager.getDriver());

		report("naturalWidth", brokenByBrowser);
		report("HTTP status", brokenByHttp);

		Assert.assertEquals(brokenByBrowser.size(), EXPECTED_BROKEN,
				"naturalWidth technique should flag exactly the 2 known-broken images (asdf.jpg, hjkl.jpg)");
		Assert.assertEquals(brokenByHttp.size(), EXPECTED_BROKEN,
				"HTTP technique should independently confirm the same 2 broken images");
	}

	// INFO because a broken-image report is a business-level result a stakeholder would read, not
	// framework noise. Each entry carries its 'why' (reason) so the log alone is enough to triage.
	private void report(String technique, List<BrokenImage> broken) {
		log.info("[{}] found {} broken image(s):", technique, broken.size());
		broken.forEach(image -> log.info("    - {}  ({})", image.src(), image.reason()));
	}
}
