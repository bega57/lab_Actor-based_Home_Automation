package at.fhv.sysarch.lab2.homeautomation.grpcdemo;


//#import

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.http.javadsl.*;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.japi.function.Function;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import at.fhv.sysarch.lab2.homeautomation.persistence.OrderPersistenceActor;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.CompletionStage;

//#import

//#server
public class GreeterServer {

    public static void main(String[] args) throws Exception {
        initSchema();

        // important to enable HTTP/2 in ActorSystem's config
        Config conf = ConfigFactory.parseString("pekko.http.server.preview.enable-http2 = on")
                .withFallback(ConfigFactory.load());
        ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "GreeterServer", conf);
        new GreeterServer(system).run();
    }

    private static void initSchema() {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:h2:./pekkojournal;DATABASE_TO_UPPER=false")) {

            java.io.InputStream sql = GreeterServer.class.getClassLoader()
                    .getResourceAsStream("create-schema.sql");

            if (sql == null) {
                System.err.println("[Schema] create-schema.sql not found!");
                return;
            }

            String ddl = new String(sql.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

            for (String stmt : ddl.split(";")) {
                String s = stmt.strip();
                if (!s.isEmpty()) {
                    conn.createStatement().execute(s);
                }
            }
            System.out.println("[Schema] H2 schema initialized");

        } catch (Exception e) {
            System.err.println("[Schema] Init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    final ActorSystem<?> system;

    public GreeterServer(ActorSystem<?> system) {
        this.system = system;
    }

    public CompletionStage<ServerBinding> run() throws Exception {

        ActorRef<OrderPersistenceActor.Command> persistence =
                system.systemActorOf(
                        OrderPersistenceActor.create(),
                        "grpcPersistence",
                        org.apache.pekko.actor.typed.Props.empty()
                );

        ActorRef<OrderProcessorActor.Command> processor =
                system.systemActorOf(
                        OrderProcessorActor.create(persistence),
                        "orderProcessor",
                        org.apache.pekko.actor.typed.Props.empty()
                );

        Function<HttpRequest, CompletionStage<HttpResponse>> service =
                OrderServiceHandlerFactory.create(
                        new OrderServiceImpl(processor, system),
                        system
                );

        CompletionStage<ServerBinding> bound =
                Http.get(system)
                        .newServerAt("127.0.0.1", 8080)
                        .bind(service);

        bound.thenAccept(binding ->
                System.out.println(
                        "gRPC server bound to: "
                                + binding.localAddress()
                )
        );

        return bound;
    }
    // #server
}
//#server