package com.demo.shop.order;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import com.demo.shop.order.CheckoutService.Order;
import com.demo.shop.order.CheckoutService.OrderLine;

@Path("/api/checkout")
public class CheckoutResource {

    public record CheckoutRequest(List<OrderLine> items) {
    }

    @Inject
    CheckoutService checkoutService;

    @POST
    public Order checkout(CheckoutRequest request) {
        try {
            return checkoutService.checkout(request == null ? null : request.items());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
