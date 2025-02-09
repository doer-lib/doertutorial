package com.doertutorial;

import com.doer.DoerExtraJson;
import com.doer.Task;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.StringReader;

@ApplicationScoped
public class ExtraJsonAugmenter {
    public static final String DOERTUTORIAL_URI = "_doertutorial_uri";
    public static final String DOERTUTORIAL_METHOD = "_doertutorial_method";

    @DoerExtraJson
    public void appendResponseInfo(Task task, WebApplicationException ex, JsonObjectBuilder extraJson) {
        Response response = ex.getResponse();
        JsonObjectBuilder json = Json.createObjectBuilder();
        appendRequestInfo(response, json);
        json.add("status", response.getStatus());
        appendResponseHeaders(response, json);
        if (response.getMediaType() != null && response.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            appendJsonBody(response, json);
        }
        extraJson.add("response", json);
    }

    private static void appendRequestInfo(Response response, JsonObjectBuilder json) {
        String method = response.getHeaderString(ExtraJsonAugmenter.DOERTUTORIAL_METHOD);
        if (method != null) {
            json.add("method", method);
        }
        String uri = response.getHeaderString(ExtraJsonAugmenter.DOERTUTORIAL_URI);
        if (uri != null) {
            json.add("uri", uri);
        }
    }

    private static void appendResponseHeaders(Response response, JsonObjectBuilder json) {
        var jsonHeaders = Json.createObjectBuilder();
        for (String name : response.getHeaders().keySet()) {
            switch (name) {
                case DOERTUTORIAL_METHOD:
                case DOERTUTORIAL_URI:
                    break;
                default:
                    jsonHeaders.add(name, response.getHeaderString(name));
            }
        }
        json.add("headers", jsonHeaders);
    }

    private static void appendJsonBody(Response response, JsonObjectBuilder json) {
        response.bufferEntity();
        try {
            String body = response.readEntity(String.class);
            if (body.length() < 2048) {
                json.add("json_body", Json.createReader(new StringReader(body)).readObject());
            } else {
                Log.warnf("Too long error response (%d). %s...", body.length(), body.substring(0, 500));
            }
        } catch (Exception e) {
            Log.warn("Can not parse error response. Skipping.", e);
        }
    }
}
