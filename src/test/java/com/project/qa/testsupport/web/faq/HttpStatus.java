package com.project.qa.testsupport.web.faq;

import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;

/**
 * Shared HTTP status-code probe for the broken-image and broken-link scanners.
 *
 * <p>Extracted so both scanners share ONE client and ONE definition of "reachable", instead of
 * drifting apart with duplicated request/timeout/redirect policy. It carries no state a caller needs
 * to construct, so it is a package-private static utility rather than an injected wrapper object.
 */
final class HttpStatus {

	private static final Logger log = LoggerFactory.getLogger(HttpStatus.class);

	// Not a real HTTP code: a sentinel for a transport-level failure (DNS, connection refused,
	// timeout) so a caller can treat "couldn't even connect" uniformly with a 4xx/5xx.
	static final int TRANSPORT_FAILURE = 599;

	// One shared, immutable, thread-safe client. followRedirects(NORMAL) stops a legitimate
	// 3xx-to-target from being mis-flagged; connectTimeout caps how long a dead host can stall.
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private HttpStatus() {
	}

	/**
	 * @return the HTTP status code for {@code url}, or {@link #TRANSPORT_FAILURE} if the request
	 *         could not complete at the transport layer.
	 */
	static int of(String url) {
		try {
			// GET (body discarded) rather than HEAD: many CDNs reject HEAD for assets, so GET is the
			// reliable choice, and discarding the body keeps it almost as cheap.
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();
			return CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
		} catch (IOException e) {
			log.debug("Request to {} failed at the transport layer: {}", url, e.getMessage());
			return TRANSPORT_FAILURE;
		} catch (InterruptedException e) {
			// Restore the interrupt flag before failing so cooperative cancellation still works.
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while checking URL: " + url, e);
		}
	}
}
