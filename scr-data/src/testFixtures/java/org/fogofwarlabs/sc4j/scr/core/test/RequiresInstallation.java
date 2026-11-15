package org.fogofwarlabs.sc4j.scr.core.test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * Marks a test class or method of the integration tier: it needs the SC:R installation ({@link ExternalInput#INSTALLATION})
 * and runs in the Gradle task {@code integrationTest}, never in {@code test}.
 * <p>
 * The test fails, and is not skipped, when the installation is not configured. The test reads the paths through
 * {@link ExternalInput#getPath()} and {@link ExternalInput#resolveFile}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("integration")
@ExtendWith(ExternalInputExtension.class)
public @interface RequiresInstallation {
}
