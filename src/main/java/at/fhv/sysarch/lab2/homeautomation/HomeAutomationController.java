package at.fhv.sysarch.lab2.homeautomation;

import at.fhv.sysarch.lab2.homeautomation.devices.*;
import at.fhv.sysarch.lab2.homeautomation.uihandler.DemoHttpServer;
import at.fhv.sysarch.lab2.homeautomation.uihandler.OrderHistoryActor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import at.fhv.sysarch.lab2.homeautomation.environment.EnvironmentActor;
import at.fhv.sysarch.lab2.homeautomation.environment.MqttEnvironmentClient;
import at.fhv.sysarch.lab2.homeautomation.uihandler.StatusAggregatorActor;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class HomeAutomationController extends AbstractBehavior<Void> {

    public static Behavior<Void> create() {
        return Behaviors.setup(HomeAutomationController::new);
    }

    private  HomeAutomationController(ActorContext<Void> context) {
        super(context);
        // TODO: consider guardians and hierarchies. Who should create and communicate with which Actors?
        // TODO: Remember: We are communicating over the Receptionist (unless fridge), thus it is most likely, that you are not passing any ActorRefs to other Actors here.
        // TODO: One exception to this rule is that you are allowed to pass the ActorRef when you are communicating through Request-Response (actor.ask())
        ActorRef<AirCondition.AirConditionCommand> airCondition =
                getContext().spawn(AirCondition.create(UUID.randomUUID().toString()), "airCondition");

        ActorRef<TemperatureSensor.TemperatureCommand> tempSensor =
                getContext().spawn(TemperatureSensor.create(), "temperatureSensor");

        ActorRef<Blinds.Command> blinds =
                getContext().spawn(Blinds.create(), "blinds");

        ActorRef<WeatherSensor.Command> weatherSensor =
                getContext().spawn(WeatherSensor.create(blinds), "weatherSensor");

        ActorRef<EnvironmentActor.Command> environment =
                getContext().spawn(
                        EnvironmentActor.create(tempSensor, weatherSensor),
                        "environment"
                );

        MqttEnvironmentClient mqtt =
                new MqttEnvironmentClient(environment);

        mqtt.start();

        ActorRef<MediaStation.Command> mediaStation =
                getContext().spawn(MediaStation.create(blinds), "mediaStation");

        ActorRef<OrderHistoryActor.Command> orderHistory =
                getContext().spawn(
                        OrderHistoryActor.create(),
                        "orderHistory"
                );

        ActorRef<Fridge.Command> fridge =
                getContext().spawn(
                        Fridge.create(orderHistory),
                        "fridge"
                );

        ActorRef<StatusAggregatorActor.Command> statusAggregator =
                getContext().spawn(
                        StatusAggregatorActor.create(
                                environment,
                                airCondition,
                                blinds,
                                mediaStation,
                                context.getSystem().scheduler()
                        ),
                        "statusAggregator"
                );


        fridge.tell(new Fridge.AddProduct("Milk", 2, 1.0, 2.5));
        fridge.tell(new Fridge.AddProduct("Red Bull", 2, 0.25, 1.5));
        fridge.tell(new Fridge.AddProduct("Pizza", 2, 0.8, 4.0));
        fridge.tell(new Fridge.AddProduct("Capri Sun", 2, 0.2, 1.0));

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
            e.printStackTrace();
        }

        getContext().getLog().info("HomeAutomation Application started - PRESS RETURN TO EXIT");

        try {
            System.in.read();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        binding
                .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
                .thenAccept(unbound -> getContext().getSystem().terminate()); // and shutdown when done
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
    }

    private HomeAutomationController onPostStop() {
        getContext().getLog().info("HomeAutomation Application stopped");
        return this;
    }
}
