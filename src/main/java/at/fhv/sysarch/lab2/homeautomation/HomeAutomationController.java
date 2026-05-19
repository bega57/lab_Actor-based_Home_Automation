package at.fhv.sysarch.lab2.homeautomation;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.uihandler.DemoHttpServer;
import at.fhv.sysarch.lab2.homeautomation.uihandler.OrderHistoryActor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;
import at.fhv.sysarch.lab2.homeautomation.environment.MqttEnvironmentClient;
import at.fhv.sysarch.lab2.homeautomation.uihandler.StatusAggregatorActor;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Guardian / root actor of the Home Automation system.
 *
 * Spawning order matters here: actors that register with the Receptionist must
 * be spawned before actors that subscribe to them – otherwise the subscription
 * may fire before the registration, and the listing will be empty for the first
 * update.  Pekko's Receptionist does send a replay for late subscribers, but
 * spawning in the right order avoids the initial empty-listing edge case.
 *
 * Order:
 *  1. AirCondition  – registers SERVICE_KEY
 *  2. TemperatureSensor – registers SERVICE_KEY, subscribes to AirCondition
 *  3. Blinds        – registers SERVICE_KEY
 *  4. WeatherSensor – registers SERVICE_KEY, subscribes to Blinds
 *  5. MediaStation  – subscribes to Blinds
 *  6. EnvironmentActor – subscribes to TempSensor + WeatherSensor, spawns simulators
 *  7. Fridge + OrderHistory
 *  8. StatusAggregator + HTTP server
 */
public class HomeAutomationController extends AbstractBehavior<Void> {

    public static Behavior<Void> create() {
        return Behaviors.setup(HomeAutomationController::new);
    }

    private HomeAutomationController(ActorContext<Void> context) {
        super(context);

        // AirCondition – registers with Receptionist
        ActorRef<AirCondition.AirConditionCommand> airCondition =
                context.spawn(
                        AirCondition.create(UUID.randomUUID().toString()),
                        "airCondition"
                );

        // TemperatureSensor – registers; subscribes to AirCondition
        context.spawn(TemperatureSensor.create(), "temperatureSensor");

        // Blinds – registers with Receptionist
        ActorRef<Blinds.Command> blinds =
                context.spawn(Blinds.create(), "blinds");

        // WeatherSensor – registers; subscribes to Blinds
        context.spawn(WeatherSensor.create(), "weatherSensor");

        // MediaStation – subscribes to Blinds (no direct ref passed)
        ActorRef<MediaStation.Command> mediaStation =
                context.spawn(MediaStation.create(), "mediaStation");

        //EnvironmentActor – subscribes to TempSensor + WeatherSensor;
        //spawns TemperatureSimulatorActor + WeatherSimulatorActor as children
        ActorRef<EnvironmentActor.Command> environment =
                context.spawn(EnvironmentActor.create(), "environment");

        //Start MQTT client for external weather source
        MqttEnvironmentClient mqtt = new MqttEnvironmentClient(environment);
        mqtt.start();

        // Fridge (spawns WeightSensor + CapacitySensor as own children)
        ActorRef<OrderHistoryActor.Command> orderHistory =
                context.spawn(OrderHistoryActor.create(), "orderHistory");

        ActorRef<Fridge.Command> fridge =
                context.spawn(Fridge.create(orderHistory), "fridge");

        // Pre-fill the fridge with some products
        fridge.tell(new Fridge.AddProduct("Milk",      2, 1.0,  2.5));
        fridge.tell(new Fridge.AddProduct("Red Bull",  2, 0.25, 1.5));
        fridge.tell(new Fridge.AddProduct("Pizza",     2, 0.8,  4.0));
        fridge.tell(new Fridge.AddProduct("Capri Sun", 2, 0.2,  1.0));

        //StatusAggregator + HTTP server
        //StatusAggregator needs direct refs because it uses Request-Response (ask)
        //against those actors – this is explicitly allowed for ask patterns.
        ActorRef<StatusAggregatorActor.Command> statusAggregator =
                context.spawn(
                        StatusAggregatorActor.create(
                                environment,
                                airCondition,
                                blinds,
                                mediaStation,
                                context.getSystem().scheduler()
                        ),
                        "statusAggregator"
                );

        final Http http = Http.get(context.getSystem());
        DemoHttpServer app = new DemoHttpServer(
                fridge,
                mediaStation,
                environment,
                statusAggregator,
                orderHistory,
                context.getSystem().scheduler()
        );
        final CompletionStage<ServerBinding> binding =
                http.newServerAt("localhost", 8084).bind(app.createRoute());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        getContext().getLog().info(
                "HomeAutomation started at http://localhost:8084 – press RETURN to exit"
        );

        try {
            System.in.read();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> context.getSystem().terminate());
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder()
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private HomeAutomationController onPostStop() {
        getContext().getLog().info("HomeAutomation stopped");
        return this;
    }
}
