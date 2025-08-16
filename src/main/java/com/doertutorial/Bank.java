package com.doertutorial;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "bankapi")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface Bank {
    @POST
    @Path("bank/processPayment")
    @Deprecated
    Check processPayment(Order order);

    @POST
    @Path("bank/processPaymentV2")
    Payment processPaymentV2(@QueryParam("id") String transactionId, Order order);

    @GET
    @Path("bank/paymentStatusV2")
    Payment checkPaymentStatus(@QueryParam("id") String transactionId);

    @POST
    @Path("bank/cancelPayment")
    void cancelPayment(Check check);

    record Check(String transactionId) {
    }

    record Payment(String transactionId, PaymentStatus status) {}

    enum PaymentStatus {
        IN_PROGRESS,
        FAILED,
        CANCELLED,
        SUCCESS,
    }
}
