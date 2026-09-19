package com.project.qa.testsupport.web.faq;

import io.qameta.allure.*;
import org.openqa.selenium.*;
import org.slf4j.*;

import java.util.*;

/**
 * Interview Q — "How do you find broken links on a page using Selenium WebDriver?"
 *
 * <p>This is the canonical recipe, and — unlike images — HTTP status IS the right tool here, because
 * a link's contract is simply "does the target resolve?":
 *
 * <ol>
 *   <li>Collect every {@code <a>} by tag name.</li>
 *   <li>Read each {@code href} (Selenium returns it already absolute).</li>
 *   <li>Filter to network-verifiable {@code http(s)} URLs and de-duplicate.</li>
 *   <li>Issue an HTTP request per URL; treat any {@code 4xx}/{@code 5xx} (or an unreachable host)
 *       as a broken link.</li>
 * </ol>
 *
 * <p><b>Why HTTP status here but naturalWidth for images:</b> the browser never auto-fetches an
 * {@code href} the way it auto-loads an {@code <img>}, so there is no rendered-state signal to read.
 * The only way to know a link is dead without clicking all of them is to probe the URL directly.
 *
 * <p><b>Why not navigate + read the page title (the old textbook line):</b> clicking each link is
 * slow, mutates browser state, and a custom 404 page often still returns a friendly title. The
 * status line is the authoritative signal.
 */
public class BrokenLinkScanner {

	private static final Logger log = LoggerFactory.getLogger(BrokenLinkScanner.class);

	/**
	 * @return one {@link BrokenLink} per unique href that did not resolve; empty if all links are healthy.
	 */
	@Step("Scan page for broken links via HTTP status codes")
	public List<BrokenLink> findBrokenLinks(WebDriver driver) {
		List<WebElement> anchors = driver.findElements(By.tagName("a"));
		log.info("Found {} <a> element(s); filtering to unique http(s) hrefs", anchors.size());

		// A LinkedHashSet de-duplicates (logo/nav/footer links repeat across a page) while preserving
		// first-seen order, so the report reads top-to-bottom and each URL is only fetched once.
		Set<String> urls = new LinkedHashSet<>();
		for (WebElement anchor : anchors) {
			String href = anchor.getAttribute("href");
			if (isVerifiable(href)) {
				urls.add(href);
			} else {
				log.debug("SKIP   -> {}", href);
			}
		}
		log.info("Verifying {} unique link(s)", urls.size());

		List<BrokenLink> broken = new ArrayList<>();
		for (String url : urls) {
			int status = HttpStatus.of(url);
			if (status >= 400) {
				log.debug("BROKEN ({}) -> {}", status, url);
				broken.add(new BrokenLink(url, describe(status)));
			} else {
				log.debug("OK     ({}) -> {}", status, url);
			}
		}

		log.info("Link scan complete: {} broken of {} verified", broken.size(), urls.size());
		return broken;
	}

	// Only http/https links point at something a server can answer for. Anchors used purely for
	// in-page behaviour (#fragments, javascript:, mailto:, tel:) are not "broken links" — verifying
	// them would only add noise and false failures.
	private boolean isVerifiable(String href) {
		if (href == null || href.isBlank()) {
			return false;
		}
		String lower = href.toLowerCase();
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	private String describe(int status) {
		return status == HttpStatus.TRANSPORT_FAILURE
				? "Unreachable (connection failed or timed out)"
				: "HTTP " + status;
	}
}
