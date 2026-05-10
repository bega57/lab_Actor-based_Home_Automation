package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

/**
 * Note: This is an incomplete demonstration how a temperature could be implemented.
 * You may (actually, you should) change the logic so that it fits into your own actor system.
 * This class only acts as a demonstration for you to see, how an actor in java && pekko is structured.
 */
public class AirCondition extends AbstractBehavior<AirCondition.AirConditionCommand> {

    public static final ServiceKey<AirConditionCommand> SERVICE_KEY =
            ServiceKey.create(
                    AirConditionCommand.class,
                    "AirConditionService"
            );
    // commands our actor is able to receive
    public interface AirConditionCommand { }
    public record GetStatus(ActorRef<StatusResponse> replyTo) implements AirConditionCommand {}
    public record StatusResponse(boolean isOn) {}
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
        context.getSystem().receptionist().tell(
                Receptionist.register(
                        SERVICE_KEY,
                        context.getSelf()
                )
        );
        getContext().getLog().info("AirCondition started");
    }

    // message handling logic = router for incoming messages and which callbacks should process them
    @Override
    public Receive<AirConditionCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(EnrichedTemperature.class, this::onReadTemperature)
                .onMessage(GetStatus.class, this::onGetStatus)
                .onSignal(PostStop.class, signal -> onPostStop())
                .build();
    }

    private boolean isOn = false;

    private Behavior<AirConditionCommand> onReadTemperature(EnrichedTemperature r) {
        getContext().getLog().info("Aircondition reading {}", r.value);

        if (r.value > 20) {
            if (!isOn) {
                isOn = true;

                getContext().getLog().info("AC turned ON (cooling)");
            }
        } else {
            if (isOn) {
                isOn = false;

                getContext().getLog().info("AC turned OFF");
            }
        }

        return Behaviors.same();
    }

    private Behavior<AirConditionCommand> onPostStop() {
        getContext().getLog().info("AirCondition actor {}-{} stopped", identifier);
        return Behaviors.same();
    }

    private Behavior<AirConditionCommand> onGetStatus(
            GetStatus msg
    ) {

        msg.replyTo.tell(
                new StatusResponse(isOn)
        );

        return Behaviors.same();
    }
}
