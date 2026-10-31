package io.github.brunostsilva.payoutops.api;

import io.github.brunostsilva.payoutops.api.PayoutResponses.CommissionRuleResponse;
import io.github.brunostsilva.payoutops.api.PayoutResponses.PayoutComparisonResponse;
import io.github.brunostsilva.payoutops.api.PayoutResponses.PayoutResponse;
import io.github.brunostsilva.payoutops.application.PayoutService;
import io.github.brunostsilva.payoutops.domain.PayoutPeriod;
import io.github.brunostsilva.payoutops.domain.SellerId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sellers/{sellerId}")
class PayoutController {

    private final PayoutService payoutService;

    PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping("/payouts")
    PayoutResponse payout(
            @PathVariable("sellerId") String sellerId,
            @RequestParam("weekOf") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        return PayoutResponse.from(payoutService.payoutFor(parseSellerId(sellerId), PayoutPeriod.weekOf(weekOf)));
    }

    @GetMapping("/payouts/comparison")
    PayoutComparisonResponse comparison(
            @PathVariable("sellerId") String sellerId,
            @RequestParam("weekOf") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        return PayoutComparisonResponse.from(
                payoutService.compareWithPreviousPeriod(parseSellerId(sellerId), PayoutPeriod.weekOf(weekOf)));
    }

    @GetMapping("/commission-rules")
    List<CommissionRuleResponse> commissionRules(@PathVariable("sellerId") String sellerId) {
        return payoutService.commissionRules(parseSellerId(sellerId)).stream()
                .map(CommissionRuleResponse::from)
                .toList();
    }

    private static SellerId parseSellerId(String raw) {
        try {
            return new SellerId(raw);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
    }
}
