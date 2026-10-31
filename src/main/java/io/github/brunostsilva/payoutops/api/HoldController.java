package io.github.brunostsilva.payoutops.api;

import io.github.brunostsilva.payoutops.api.HoldDecisionRequests.ApproveRequest;
import io.github.brunostsilva.payoutops.api.HoldDecisionRequests.RejectRequest;
import io.github.brunostsilva.payoutops.api.PayoutResponses.PayoutHoldResponse;
import io.github.brunostsilva.payoutops.application.PayoutHoldService;
import io.github.brunostsilva.payoutops.domain.HoldStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Where analysts review the holds proposed by the agent. */
@RestController
@RequestMapping("/api/holds")
class HoldController {

    private final PayoutHoldService holdService;

    HoldController(PayoutHoldService holdService) {
        this.holdService = holdService;
    }

    @GetMapping
    List<PayoutHoldResponse> list(@RequestParam(name = "status", defaultValue = "PENDING") HoldStatus status) {
        return holdService.list(status).stream().map(PayoutHoldResponse::from).toList();
    }

    @GetMapping("/{id}")
    PayoutHoldResponse get(@PathVariable("id") UUID id) {
        return PayoutHoldResponse.from(holdService.get(id));
    }

    @PostMapping("/{id}/approve")
    PayoutHoldResponse approve(@PathVariable("id") UUID id, @Valid @RequestBody ApproveRequest request) {
        return PayoutHoldResponse.from(holdService.approve(id, request.analyst(), request.note()));
    }

    @PostMapping("/{id}/reject")
    PayoutHoldResponse reject(@PathVariable("id") UUID id, @Valid @RequestBody RejectRequest request) {
        return PayoutHoldResponse.from(holdService.reject(id, request.analyst(), request.note()));
    }
}
