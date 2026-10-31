package io.github.brunostsilva.payoutops.api;

import io.github.brunostsilva.payoutops.application.SellerNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(SellerNotFoundException.class)
    ProblemDetail sellerNotFound(SellerNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Seller not found");
        return problem;
    }
}
