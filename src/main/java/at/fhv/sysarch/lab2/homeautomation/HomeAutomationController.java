package at.fhv.sysarch.lab2.homeautomation;

import at.fhv.sysarch.lab2.homeautomation.devices.AirCondition;
import at.fhv.sysarch.lab2.homeautomation.devices.TemperatureSensor;
import at.fhv.sysarch.lab2.homeautomation.uihandler.DemoHttpServer;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;

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
        ActorRef<AirCondition.AirConditionCommand> airCondition = getContext().spawn(AirCondition.create(UUID.randomUUID().toString()), "airCondition");
        ActorRef<TemperatureSensor.TemperatureCommand> tempSensor = getContext().spawn(TemperatureSensor.create(airCondition), "temperatureSensor");

        final Http http = Http.get(context.getSystem());
        DemoHttpServer app = new DemoHttpServer();
        final CompletionStage<ServerBinding> binding = http.newServerAt("localhost", 8084).bind(app.createRoute());

        getContext().getLog().info("HomeAutomation Application started - PRESS RETURN TO EXIT");

        try {
            System.in.read(); // let it run until user presses return
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
