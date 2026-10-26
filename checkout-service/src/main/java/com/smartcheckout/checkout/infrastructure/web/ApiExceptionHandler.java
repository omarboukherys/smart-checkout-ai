package com.smartcheckout.checkout.infrastructure.web;

import com.smartcheckout.checkout.application.CheckoutNotFoundException;
import com.smartcheckout.checkout.domain.DomainException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Turns exceptions into RFC 9457 "problem details" JSON responses:
 * 400 malformed request, 404 unknown checkout, 409 concurrent replay, 422 business rule violated.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);

    @ExceptionHandler(DomainException.class)
    ProblemDetail onBusinessRule(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(UNPROCESSABLE, ex.getMessage());
        problem.setTitle("Business rule violated");
        return problem;
    }

    @ExceptionHandler(CheckoutNotFoundException.class)
    ProblemDetail onNotFound(CheckoutNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Checkout not found");
        return problem;
    }

    /** Two requests with the same Idempotency-Key arrived at the same time: the unique constraint stopped one. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onConflict(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A request with the same Idempotency-Key is already being processed, retry later");
        problem.setTitle("Concurrent request");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.status(status).body(validationProblem(status, errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<String> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> describe(result.getMethodParameter().getParameterName(), error)))
                .toList();
        return ResponseEntity.status(status).body(validationProblem(status, errors));
    }

    private static String describe(String parameter, MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }
        return parameter + ": " + error.getDefaultMessage();
    }

    private static ProblemDetail validationProblem(HttpStatusCode status, List<String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "The request is invalid");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
