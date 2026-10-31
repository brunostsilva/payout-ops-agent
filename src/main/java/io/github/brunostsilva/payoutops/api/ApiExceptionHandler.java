package io.github.brunostsilva.payoutops.api;

import io.github.brunostsilva.payoutops.agent.AgentLoopLimitExceededException;
import io.github.brunostsilva.payoutops.application.HoldNotFoundException;
import io.github.brunostsilva.payoutops.application.SellerNotFoundException;
import io.github.brunostsilva.payoutops.domain.HoldAlreadyDecidedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
