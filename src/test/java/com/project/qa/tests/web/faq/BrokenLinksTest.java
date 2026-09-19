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
 * Interview Q — "Explain how you can find broken links on a page using Selenium WebDriver."
 *
 * <p><b>Fixture:</b> {@code the-internet.herokuapp.com/status_codes} links to
 * {@code /status_codes/{200,301,404,500}}. The 404 and 500 targets are the deterministic broken
 * links; the page also carries third-party links (GitHub, IANA) whose uptime we don't control.
 *
 * <p>The test therefore asserts on that KNOWN broken subset ({@code contains 404 and 500}) rather
 * than an exact total — asserting an exact count would make the test hostage to external sites and
 * flaky by design. This is the standard "pin the deterministic signal, tolerate the noise" trade-off.
 */
public class BrokenLinksTest extends BaseTest {

	private static final Logger log = LoggerFactory.getLogger(BrokenLinksTest.class);
	private static final String DEMO_URL = "https://the-internet.herokuapp.com/status_codes";

	private final BrokenLinkScanner scanner = new BrokenLinkScanner();

	@Test(groups = TestGroups.WEB)
	@Description("Finds broken links by collecting every <a> and flagging any href that returns 4xx/5xx.")
	public void detectsBrokenLinksOnPage() {
		DriverManager.getDriver().get(DEMO_URL);

		List<BrokenLink> broken = scanner.findBrokenLinks(DriverManager.getDriver());
		report(broken);

		List<String> brokenHrefs = broken.stream().map(BrokenLink::href).toList();

		Assert.assertTrue(brokenHrefs.stream().anyMatch(href -> href.endsWith("/status_codes/404")),
				"Expected the 404 link to be reported broken. Broken links found: " + brokenHrefs);
		Assert.assertTrue(brokenHrefs.stream().anyMatch(href -> href.endsWith("/status_codes/500")),
				"Expected the 500 link to be reported broken. Broken links found: " + brokenHrefs);
	}

	// INFO because a broken-link report is a business-level result, not framework noise; each entry
	// carries its 'why' so the log alone is enough to triage without re-running.
	private void report(List<BrokenLink> broken) {
		log.info("Found {} broken link(s):", broken.size());
		broken.forEach(link -> log.info("    - {}  ({})", link.href(), link.reason()));
	}
}
