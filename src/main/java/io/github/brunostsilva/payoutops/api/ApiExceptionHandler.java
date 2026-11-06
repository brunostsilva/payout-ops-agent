package io.github.brunostsilva.payoutops.api;

import io.github.brunostsilva.payoutops.agent.AgentLoopLimitExceededException;
import io.github.brunostsilva.payoutops.application.HoldNotFoundException;
import io.github.brunostsilva.payoutops.application.SellerNotFoundException;
import io.github.brunostsilva.payoutops.domain.HoldAlreadyDecidedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to RFC 9457 problem details. The response is only logged at DEBUG: unknown ids
 * are routine client errors, and conflicts and model failures are already logged at WARN, with their
 * details, by {@code PayoutHoldService} and {@code PayoutAgent}, so each event is one WARN line. Stack
 * traces are not logged: these are expected outcomes, not bugs.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SellerNotFoundException.class)
    ProblemDetail sellerNotFound(SellerNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Seller not found", exception);
    }

    @ExceptionHandler(HoldNotFoundException.class)
    ProblemDetail holdNotFound(HoldNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Hold not found", exception);
    }

    @ExceptionHandler(HoldAlreadyDecidedException.class)
    ProblemDetail holdAlreadyDecided(HoldAlreadyDecidedException exception) {
        return problem(HttpStatus.CONFLICT, "Hold already decided", exception);
    }

    @ExceptionHandler(AgentLoopLimitExceededException.class)
    ProblemDetail agentLoopLimitExceeded(AgentLoopLimitExceededException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "The model did not produce an answer", exception);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException exception) {
        log.atDebug()
                .setMessage("{} {}: {}")
                .addArgument(status.value())
                .addArgument(title)
                .addArgument(exception.getMessage())
                .addKeyValue("http.response.status_code", status.value())
                .addKeyValue("error.type", exception.getClass().getSimpleName())
                .log();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
