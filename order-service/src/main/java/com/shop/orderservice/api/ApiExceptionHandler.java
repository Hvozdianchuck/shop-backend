package com.shop.orderservice.api;

import com.shop.orderservice.application.UnknownOrderException;
import com.shop.orderservice.application.UnknownProductsException;
import com.shop.orderservice.domain.CatalogUnavailableException;
import com.shop.orderservice.domain.OrderNotCancellableException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail unknownProducts(UnknownProductsException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Some products are unknown or no longer sold");
        problem.setProperty("unknownSkus", e.getSkus());
        return problem;
    }

    @ExceptionHandler
    ResponseEntity<ProblemDetail> catalogUnavailable(CatalogUnavailableException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Prices are temporarily unavailable, try again later");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "10")
                .body(problem);
    }

    @ExceptionHandler
    ProblemDetail tooManyRequests(RequestNotPermitted e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many orders, slow down");
    }

    @ExceptionHandler
    ProblemDetail unknownOrder(UnknownOrderException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail notCancellable(OrderNotCancellableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail concurrentChange(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The order was changed at the same time, reload it and try again");
    }
}
