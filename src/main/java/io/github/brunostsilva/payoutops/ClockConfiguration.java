package io.github.brunostsilva.payoutops;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    /** The server's zone: the agent derives "today" (and so "this week") from it. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
