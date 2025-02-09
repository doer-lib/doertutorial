package com.doertutorial;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * In this filter, we set custom http headers into Response to deliver Request's URI and METHOD values
 * to WebApplicationException handlers.
 */
@Provider
public class CatchUriFilter implements ClientResponseFilter {
    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        if (responseContext.getStatus() >= 400) {
            var headers = responseContext.getHeaders();
            headers.add(ExtraJsonAugmenter.DOERTUTORIAL_URI, "" + requestContext.getUri());
            headers.add(ExtraJsonAugmenter.DOERTUTORIAL_METHOD, requestContext.getMethod());
        }
    }
}
