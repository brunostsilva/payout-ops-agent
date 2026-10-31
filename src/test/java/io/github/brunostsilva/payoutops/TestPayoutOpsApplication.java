package io.github.brunostsilva.payoutops;

import org.springframework.boot.SpringApplication;

/** Runs the app locally against a throwaway Postgres container: {@code mvn spring-boot:test-run}. */
public class TestPayoutOpsApplication {

    public static void main(String[] args) {
        SpringApplication.from(PayoutOpsApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
