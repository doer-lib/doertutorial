package it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static it.Testbed.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrderProcessingITCase {
    @BeforeEach
    void init() throws Exception {
        startTestbed();
        WireMock.reset();
    }

    @Test
    void order_should_become_shipped() {
        String location = RestAssured.with()
                .redirects()
                .follow(false)
                .formParam("customer", "Alice")
                .formParam("items", "a pen")
                .post("/orders/submit")
                .then()
                .statusCode(303)
                .extract()
                .header("Location");

        RestAssured.get(location)
                .then()
                .statusCode(200)
                .body("order.customer", equalTo("Alice"))
                .body("order.items", equalTo("a pen"));

        waitForConditionOrDeadline(
                () -> RestAssured.get(location).then(),
                r -> r.extract().jsonPath().getString("task.status") == null,
                Instant.now().plusSeconds(60)
        ).statusCode(200)
                .body("order.status", equalTo("SHIPPED"))
                .body("order.deliveryTrackingId", equalTo("mocked-shipping-token"))
                .body("task.status", nullValue());

        verify(postRequestedFor(urlPathMatching("/warehouse/reserve"))
                .withRequestBody(matchingJsonPath("items", WireMock.equalTo("a pen"))));
        verify(postRequestedFor(urlPathMatching("/bank/processPayment"))
                .withRequestBody(matchingJsonPath("customer", WireMock.equalTo("Alice"))));
        verify(postRequestedFor(urlPathMatching("/warehouse/ship"))
                .withRequestBody(matchingJsonPath("customer", WireMock.equalTo("Alice")))
                .withRequestBody(matchingJsonPath("items", WireMock.equalTo("a pen"))));
    }

    @Test
    void failed_shipping_should_cancel_payment_and_booking() {
        stubFor(post("/warehouse/ship")
                .willReturn(status(501)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"Server failure\"}")));

        String location = RestAssured.with()
                .redirects()
                .follow(false)
                .formParam("customer", "Alice")
                .formParam("items", "a pen")
                .post("/orders/submit")
                .then()
                .statusCode(303)
                .extract()
                .header("Location");

        long taskId = waitForConditionOrDeadline(
                () -> RestAssured.get(location).then().extract().jsonPath(),
                json -> json.getString("task.failingSince") != null && !json.getBoolean("task.inProgress"),
                Instant.now().plusSeconds(5)
        ).getLong("task.id");

        makeTaskOlder(taskId, "10 min");

        waitForConditionOrDeadline(
                () -> RestAssured.get(location).then(),
                r -> r.extract().jsonPath().getString("task.status") == null,
                Instant.now().plusSeconds(60)
        ).statusCode(200)
                .body("order.status", equalTo("REJECTED"))
                .body("order.rejectReason", equalTo("Unable to ship the order."))
                .body("order.deliveryTrackingId", nullValue())
                .body("task.status", nullValue());

        verify(postRequestedFor(urlPathMatching("/warehouse/cancel"))
                .withRequestBody(matchingJsonPath("token", WireMock.equalTo("mocked-token"))));
        verify(postRequestedFor(urlPathMatching("/bank/cancelPayment"))
                .withRequestBody(matchingJsonPath("transactionId", WireMock.equalTo("mocked-transactionId"))));
    }

    @Test
    void notify_manager_when_payment_cancellation_failed() throws IOException {
        stubFor(post("/warehouse/ship")
                .willReturn(status(501)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"Server failure\"}")));
        stubFor(post("/bank/cancelPayment")
                .willReturn(status(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"test-bank-system-error\":\"test-bank-system-error\"}")));

        String location = RestAssured.with()
                .redirects()
                .follow(false)
                .formParam("customer", "Alice")
                .formParam("items", "a pen")
                .post("/orders/submit")
                .then()
                .statusCode(303)
                .extract()
                .header("Location");

        long taskId = waitForConditionOrDeadline(
                () -> RestAssured.get(location).then().extract().jsonPath(),
                json -> json.getString("task.failingSince") != null && !json.getBoolean("task.inProgress"),
                Instant.now().plusSeconds(5)
        ).getLong("task.id");

        // Skip /warehouse/ship retries
        makeTaskOlder(taskId, "10 min");

        waitForConditionOrDeadline(
                () -> RestAssured.get(location).then().extract().jsonPath(),
                json -> json.getString("task.failingSince") != null && !json.getBoolean("task.inProgress") &&
                        json.getString("order.rejectReason") != null,
                Instant.now().plusSeconds(5)
        );

        // Skip /bank/cancelPayment retries
        makeTaskOlder(taskId, "30 min");

        waitForConditionOrDeadline(
                () -> RestAssured.get(location).then(),
                r -> r.extract().jsonPath().getString("task.status") == null,
                Instant.now().plusSeconds(60)
        ).statusCode(200)
                .body("order.status", equalTo("REJECTED"))
                .body("order.rejectReason", equalTo("Unable to ship the order."))
                .body("order.deliveryTrackingId", nullValue())
                .body("task.status", nullValue());

        String logs = Files.readString(Path.of("target", "app-out.txt"), StandardCharsets.UTF_8);
        assertTrue(logs.contains("from doertutorial@local to [integration-order-manager@test]"));
        assertTrue(logs.contains("test-bank-system-error"));

        verify(postRequestedFor(urlPathMatching("/warehouse/cancel"))
                .withRequestBody(matchingJsonPath("token", WireMock.equalTo("mocked-token"))));
        verify(postRequestedFor(urlPathMatching("/bank/cancelPayment"))
                .withRequestBody(matchingJsonPath("transactionId", WireMock.equalTo("mocked-transactionId"))));
    }
}
