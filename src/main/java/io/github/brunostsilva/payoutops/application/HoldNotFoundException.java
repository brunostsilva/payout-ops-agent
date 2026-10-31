package io.github.brunostsilva.payoutops.application;

import java.util.UUID;

public class HoldNotFoundException extends RuntimeException {

    public HoldNotFoundException(UUID holdId) {
        super("Hold " + holdId + " not found");
    }
}
