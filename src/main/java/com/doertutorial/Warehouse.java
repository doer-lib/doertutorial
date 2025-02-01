package com.doertutorial;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "warehouse")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface Warehouse {

    @POST
    @Path("warehouse/reserve")
    Reservation reserveGoods(Order order);

    @POST
    @Path("warehouse/ship")
    TrackId shipTheOrder(Order order);

    @POST
    @Path("warehouse/cancel")
    void cancelReservation(Reservation reservation);

    record Reservation(String token) {
    }

    record TrackId(String token) {
    }
}

