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
    Check processPayment(Order order);

    @POST
    @Path("bank/cancelPayment")
    void cancelPayment(Check check);

    record Check(String transactionId) {
    }
}
