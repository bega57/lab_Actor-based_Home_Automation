package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.Receptionist;


/**
 * Note: This is an incomplete demonstration how a temperature sensor could be implemented.
 * You may (actually, you should) change the logic so that it fits into your own actor system.
 * This class only acts as a demonstration for you to see, how an actor in java && pekko is structured.
 */
public class TemperatureSensor extends AbstractBehavior<TemperatureSensor.TemperatureCommand> {

    // commands our actor is able to receive
    public interface TemperatureCommand { }
    private record AirConditionListing(
            Receptionist.Listing listing
    ) implements TemperatureCommand {}
    public record ReadTemperature(double value) implements TemperatureCommand  { } // you may also use normal java classes instead of records, but this will result in lots of boilerplate (getter, setter, constructor)

    // factory function called when a new instance of this actor is created
    public static Behavior<TemperatureCommand> create() {
        return Behaviors.setup(TemperatureSensor::new);
    }

    // mutable/immutable state variables of the actor defined here.
    private ActorRef<AirCondition.AirConditionCommand> airCondition;

    // constructor initializing the actor
    public TemperatureSensor(
            ActorContext<TemperatureCommand> context
    ) {

        super(context);

        ActorRef<Receptionist.Listing> listingAdapter =
                context.messageAdapter(
                        Receptionist.Listing.class,
                        AirConditionListing::new
                );

        context.getSystem().receptionist().tell(
                Receptionist.subscribe(
                        AirCondition.SERVICE_KEY,
                        listingAdapter
                )
        );

        getContext().getLog().info("TemperatureSensor started");
    }

    // message handling logic = router for incoming messages and which callbacks should process them
    @Override
    public Receive<TemperatureCommand> createReceive() {
        return newReceiveBuilder()

                .onMessage(
                        AirConditionListing.class,
                        this::onAirConditionListing
                )

                .onMessage(
                        ReadTemperature.class,
                        this::onReadTemperature
                )
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    // handler method for ReadTemperature command
    private Behavior<TemperatureCommand> onReadTemperature(ReadTemperature r) {
        getContext().getLog().info("TemperatureSensor received {}", r.value);

        if (airCondition != null) {

            airCondition.tell(
                    new AirCondition.EnrichedTemperature(
                            r.value,
                            "Celsius"
                    )
            );
        }

        return this;
    }

    private Behavior<TemperatureCommand> onAirConditionListing(
            AirConditionListing msg
    ) {

        var instances =
                msg.listing.getServiceInstances(
                        AirCondition.SERVICE_KEY
                );

        instances.stream().findFirst().ifPresent(ref -> {

            airCondition = ref;

            getContext().getLog().info(
                    "AirCondition found via Receptionist"
            );
        });

        return this;
    }

    // shutdown-hook called when actor is stopped.
    private TemperatureSensor onPostStop() {
        getContext().getLog().info("TemperatureSensor actor {}-{} stopped");
        return this;
    }

}
