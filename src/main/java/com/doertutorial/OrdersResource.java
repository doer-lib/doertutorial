package com.doertutorial;

import com.doer.DoerService;
import com.doer.Task;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("orders")
public class OrdersResource {
    @Inject
    OrderDao orderDao;
    @Inject
    DoerService doerService;
    @Inject
    OrderProcessor orderProcessor;

    @POST
    @Path("submit")
    public Response submitNewOrder(
            @FormParam("customer") String customer,
            @FormParam("items") String items) throws SQLException {
        Order order = new Order();
        order.setCustomer(customer);
        order.setItems(items);
        orderProcessor.saveNewOrder(order);
        doerService.triggerTaskReloadFromDb(order.getTaskId());
        Log.infof("Order submitted: %s", order.getId());
        return Response.seeOther(URI.create("/orders/" + order.getId()))
                .build();
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOrder(@PathParam("id") UUID id) throws Exception {
        // Custom json rendering (for debugging purpose)
        JsonbConfig jsonbConfig = new JsonbConfig()
                .withFormatting(true)
                .withNullValues(true)
                .withPropertyOrderStrategy(PropertyOrderStrategy.LEXICOGRAPHICAL);
        try (Jsonb jsonb = JsonbBuilder.create(jsonbConfig)) {
            Order order = orderDao.findOrderById(id);
            if (order == null) {
                throw new NotFoundException("Order not found");
            }
            Task task = (order.getTaskId() != null ? doerService.loadTask(order.getTaskId()) : null);
            Map<String, Object> map = new HashMap<>();
            map.put("order", order);
            map.put("task", task);
            String json = jsonb.toJson(map);
            return Response.ok(json, MediaType.APPLICATION_JSON_TYPE)
                    .build();
        }
    }
}
