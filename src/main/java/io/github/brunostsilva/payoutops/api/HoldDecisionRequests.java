package io.github.brunostsilva.payoutops.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies for deciding a hold. The analyst comes from the body until authentication is added. */
public final class HoldDecisionRequests {

    private HoldDecisionRequests() {
    }

    public record ApproveRequest(@NotBlank @Size(max = 100) String analyst, @Size(max = 1000) String note) {
    }

    /** Rejections must be justified, so the note is required. */
    public record RejectRequest(@NotBlank @Size(max = 100) String analyst, @NotBlank @Size(max = 1000) String note) {
    }
}
