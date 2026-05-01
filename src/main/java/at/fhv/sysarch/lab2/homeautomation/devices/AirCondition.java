package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

/**
 * Note: This is an incomplete demonstration how a temperature could be implemented.
 * You may (actually, you should) change the logic so that it fits into your own actor system.
 * This class only acts as a demonstration for you to see, how an actor in java && pekko is structured.
 */
public class AirCondition extends AbstractBehavior<AirCondition.AirConditionCommand> {

    // commands our actor is able to receive
    public interface AirConditionCommand { }
    public record PowerAirCondition(boolean value) implements AirConditionCommand { }
    public record EnrichedTemperature(double value, String unit) implements AirConditionCommand { }

    // factory function called when a new instance of this actor is created
    public static Behavior<AirConditionCommand> create(String identifier) {
        return Behaviors.setup(context -> new AirCondition(context, identifier));
    }

    // mutable/immutable state variables of the actor defined here.
    private final String identifier;

    // constructor initializing the actor
    public AirCondition(ActorContext<AirConditionCommand> context, String identifier) {
        super(context);
        this.identifier = identifier;
        getContext().getLog().info("AirCondition started");
    }

    // message handling logic = router for incoming messages and which callbacks should process them
    @Override
    public Receive<AirConditionCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(EnrichedTemperature.class, this::onReadTemperature)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private Behavior<AirConditionCommand> onReadTemperature(EnrichedTemperature r) {
        getContext().getLog().info("Aircondition reading {}", r.value);
        // TODO: process temperature

        return Behaviors.same();
    }

    private AirCondition onPostStop() {
        getContext().getLog().info("AirCondition actor {}-{} stopped", identifier);
        return this;
    }
}
