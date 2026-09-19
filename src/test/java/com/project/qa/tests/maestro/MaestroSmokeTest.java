package com.project.qa.tests.maestro;

import com.project.qa.framework.configuration.*;
import com.project.qa.framework.maestro.*;
import com.project.qa.testsupport.constants.*;
import org.slf4j.*;
import org.testng.*;
import org.testng.annotations.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Surfaces every Maestro YAML flow as an individual TestNG test.
 *
 * WHY: it removes the Maestro CLI from a contributor's daily workflow. Drop a .yaml into
 * flows/ and it is auto-discovered and run by `mvn test -Pmaestro`, appearing in the same Allure
 * report and CI gate as the Selenium/Appium suites — no bespoke commands to memorise.
 *
 * Deliberately does NOT extend BaseTest: Maestro drives the device over its own adb bridge, so an
 * Appium/WebDriver session would waste a device slot and add nothing.
 */
public class MaestroSmokeTest {

	private static final Logger log = LoggerFactory.getLogger(MaestroSmokeTest.class);

	// One TestNG invocation per flow file, sorted so numbered flows (01, 02, ...) run in order.
	@DataProvider(name = "flows")
	public Object[][] flows() throws IOException {
		Path flowsDir = Path.of(ConfigReader.getMaestroFlowsDir());
		if (!Files.isDirectory(flowsDir)) {
			throw new SkipException("Maestro flows directory not found: " + flowsDir.toAbsolutePath());
		}
		try (Stream<Path> entries = Files.list(flowsDir)) {
			List<Path> flows = entries
					.filter(MaestroSmokeTest::isFlowFile)
					.sorted()
					.collect(Collectors.toList());
			if (flows.isEmpty()) {
				throw new SkipException("No Maestro flows found under " + flowsDir.toAbsolutePath());
			}
			log.info("Discovered {} Maestro flow(s) under {}", flows.size(), flowsDir);
			return flows.stream().map(flow -> new Object[]{flow}).toArray(Object[][]::new);
		}
	}

	@Test(groups = TestGroups.MAESTRO, dataProvider = "flows")
	public void runFlow(Path flow) {
		MaestroResult result = MaestroRunner.runFlow(
				flow, ConfigReader.getMaestroExecutable(), ConfigReader.getMaestroFlowEnv());

		Assert.assertTrue(result.passed(),
				"Maestro flow '" + result.flowName() + "' failed (exit=" + result.exitCode()
						+ "). See the [maestro] DEBUG logs above and ~/.maestro/tests/<timestamp> "
						+ "for the captured screenshot + view hierarchy.");
	}

	private static boolean isFlowFile(Path path) {
		String name = path.getFileName().toString();
		return name.endsWith(".yaml") || name.endsWith(".yml");
	}
}
