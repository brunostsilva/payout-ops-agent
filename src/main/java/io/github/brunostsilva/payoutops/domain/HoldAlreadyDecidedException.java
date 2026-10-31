package io.github.brunostsilva.payoutops.domain;

import java.util.Locale;
import java.util.UUID;

public class HoldAlreadyDecidedException extends RuntimeException {

    public HoldAlreadyDecidedException(UUID holdId, HoldStatus status) {
        super("Hold " + holdId + " was already " + status.name().toLowerCase(Locale.ROOT));
    }
}
