package io.github.brunostsilva.payoutops;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;

/**
 * Runs the app locally against a throwaway Postgres container with the demo data loaded:
 * {@code mvn spring-boot:test-run}.
 */
public class TestPayoutOpsApplication {

    public static void main(String[] args) {
        String[] withDemoProfile = Stream.concat(Stream.of("--spring.profiles.active=demo"), Arrays.stream(args))
                .toArray(String[]::new);
        SpringApplication.from(PayoutOpsApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(withDemoProfile);
    }
}
