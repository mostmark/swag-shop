package com.demo.shop.api;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.smallrye.mutiny.Uni;

/** Passes a service's answer on to the browser unchanged, errors (such as 400 for an invalid order) included. */
final class Relay {

    private Relay() {
    }

    static Uni<Response> relay(Uni<Response> call) {
        return call
                .onFailure(WebApplicationException.class)
                .recoverWithItem(failure -> ((WebApplicationException) failure).getResponse())
                .map(Relay::copy);
    }

    private static Response copy(Response upstream) {
        MediaType type = upstream.getMediaType() != null ? upstream.getMediaType() : MediaType.APPLICATION_JSON_TYPE;
        return Response.status(upstream.getStatus()).type(type).entity(upstream.readEntity(String.class)).build();
    }
}
