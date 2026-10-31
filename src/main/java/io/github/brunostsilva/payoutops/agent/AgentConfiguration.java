package io.github.brunostsilva.payoutops.agent;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AgentConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
