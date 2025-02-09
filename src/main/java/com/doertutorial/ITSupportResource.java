package com.doertutorial;

import com.doer.DoerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("it-support")
@Produces(MediaType.APPLICATION_JSON)
public class ITSupportResource {

    @Inject
    DoerService doerService;

    @GET
    @Path("reload-queues")
    public String reloadQueues() {
        doerService.triggerQueuesReloadFromDb();
        return "{\"reload\": \"Ok\"}\n";
    }
}
