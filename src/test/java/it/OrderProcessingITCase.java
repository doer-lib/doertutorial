package it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
