package it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class Testbed {
    static int WIREMOCK_PORT = 8085;
    static int PG_PORT = 9432;
    static int APP_PORT = 8080;

    public static Process pg;
    public static Process wiremock;
    public static Process app;
    public static Connection con;

    static {
        Runtime.getRuntime()
                .addShutdownHook(new Thread(Testbed::stopTestbed, "Testbed cleanup"));
    }

    public static void startTestbed() throws Exception {
        String jdbcUrl = "jdbc:postgresql://localhost:" + PG_PORT + "/quarkus";
        if (pg == null) {
            File out = new File("target", "pg-out.txt");
            File err = new File("target", "pg-err.txt");
            out.delete();
            err.delete();
            pg = new ProcessBuilder("docker", "run", "--rm",
                    "-e", "POSTGRES_DB=quarkus",
                    "-e", "POSTGRES_USER=quarkus",
                    "-e", "POSTGRES_PASSWORD=quarkus",
                    "-p", PG_PORT + ":5432", "postgres")
                    .redirectOutput(out)
                    .redirectError(err)
                    .start();
            waitTextInFile(err, "database system is ready to accept connections", Duration.ofMinutes(1));
        }
        if (wiremock == null) {
            String pwd = new File("").getAbsolutePath();
            File out = new File("target", "wiremock-out.txt");
            File err = new File("target", "wiremock-err.txt");
            out.delete();
            err.delete();
            wiremock = new ProcessBuilder("docker", "run", "--rm", "-p", WIREMOCK_PORT + ":8080", "--name", "wiremock",
                    "-v", pwd + "/src/test/resources/wiremock:/home/wiremock",
                    "wiremock/wiremock:3.10.0")
                    .redirectError(err)
                    .redirectOutput(out)
                    .start();
            waitTextInFile(out, "port:", Duration.ofMinutes(1));
            WireMock.configureFor("localhost", WIREMOCK_PORT);
        }
        if (app == null) {
            File out = new File("target", "app-out.txt");
            File err = new File("target", "app-err.txt");
            out.delete();
            err.delete();
            ProcessBuilder builder = new ProcessBuilder("java", "-jar", "target/quarkus-app/quarkus-run.jar")
                    .redirectOutput(out)
                    .redirectError(err);
            Map<String, String> env = builder.environment();
            env.put("QUARKUS_DATASOURCE_JDBC_URL", jdbcUrl);
            env.put("QUARKUS_DATASOURCE_USERNAME", "quarkus");
            env.put("QUARKUS_DATASOURCE_PASSWORD", "quarkus");
            env.put("QUARKUS_MAILER_MOCK", "true");
            env.put("ORDER_MANAGER_EMAIL", "integration-order-manager@test");
            app = builder.start();
            waitTextInFile(out, "Profile prod activated", Duration.ofMinutes(1));
            RestAssured.baseURI = "http://localhost";
            RestAssured.port = APP_PORT;
            RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        }
        if (con == null) {
            con = DriverManager.getConnection(jdbcUrl, "quarkus", "quarkus");
        }
    }

    public static void stopTestbed() {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException e) {
                System.err.println("Connection close error. " + e.getMessage());
            }
            con = null;
        }
        if (app != null) {
            try {
                app.getOutputStream().close();
            } catch (IOException e) {
                System.err.println("App STD_IN close error. " + e.getMessage());
            }
            app.destroy();
            app = null;
        }
        if (wiremock != null) {
            try {
                wiremock.getOutputStream().close();
            } catch (IOException e) {
                System.err.println("Wiremock STD_IN close error. " + e.getMessage());
            }
            wiremock.destroy();
            wiremock = null;
        }
        if (pg != null) {
            try {
                pg.getInputStream().close();
            } catch (IOException e) {
                System.err.println("PG STD_IN close error. " + e.getMessage());
            }
            pg.destroy();
            pg = null;
        }
    }

    public static void waitTextInFile(File file, String text, Duration duration) {
        Instant deadline = Instant.now().plus(duration);
        while (Instant.now().isBefore(deadline)) {
            try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
                if (reader.lines().anyMatch(line -> line.contains(text))) {
                    return;
                }
            } catch (IOException e) {
                // skip
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public static <R> R waitForConditionOrDeadline(Supplier<R> call, Function<R, Boolean> condition, Instant deadline) {
        R result;
        do {
            result = call.get();
            if (Boolean.TRUE == condition.apply(result)) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        } while (Instant.now().isBefore(deadline));
        return result;
    }

    public static void makeTaskOlder(long taskId, String pgInterval) {
        String sql = """
                UPDATE tasks SET created = created - ?::INTERVAL,
                    modified = modified - ?::INTERVAL,
                    failing_since = failing_since - ?::INTERVAL,
                    version = version + 1
                WHERE id = ?
                """;
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, pgInterval);
            pst.setString(2, pgInterval);
            pst.setString(3, pgInterval);
            pst.setLong(4, taskId);
            pst.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        RestAssured.get("/it-support/reload-queues")
                .then()
                .statusCode(200)
                .body("reload", Matchers.equalTo("Ok"));
    }
}
