package com.demo.shop.api;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** A service that is down or restarting gives 503 instead of a stack trace. */
public class ServiceUnavailableMapper {

    @ServerExceptionMapper
    public Response unavailable(ProcessingException e) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity("A shop service is not available: " + e.getMessage())
                .build();
    }
}
