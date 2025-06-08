package de.berlin.htw.boundary;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Exception mapper to handle validation errors and return proper HTTP 400 responses
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        // Extract the validation error message
        String message = exception.getMessage();

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(message)
                .build();
    }
}