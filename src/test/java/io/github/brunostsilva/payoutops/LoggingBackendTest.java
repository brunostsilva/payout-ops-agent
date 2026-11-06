package io.github.brunostsilva.payoutops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.log4j2.Log4J2LoggingSystem;

/** Guards the Logback → Log4j2 switch: SLF4J must bind to Log4j2 and Spring Boot must drive Log4j2. */
class LoggingBackendTest {

    @Test
    void slf4jIsBackedByLog4j2() {
        assertThat(LoggerFactory.getILoggerFactory().getClass().getName()).startsWith("org.apache.logging.slf4j");
    }

    @Test
    void springBootUsesTheLog4j2LoggingSystem() {
        assertThat(LoggingSystem.get(getClass().getClassLoader())).isInstanceOf(Log4J2LoggingSystem.class);
    }
}
