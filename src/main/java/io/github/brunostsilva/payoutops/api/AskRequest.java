package io.github.brunostsilva.payoutops.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(@NotBlank @Size(max = 2000) String question) {
}
