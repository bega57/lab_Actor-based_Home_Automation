package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class CapacitySensorActor extends AbstractBehavior<CapacitySensorActor.Command> {

    public interface Command {}

    public record CheckCapacity(
            int currentItems,
            int addedItems,
            int maxItems,
            ActorRef<CapacityResponse> replyTo
    ) implements Command {}

    public record CapacityResponse(boolean allowed) {}

    public static Behavior<Command> create() {
        return Behaviors.setup(CapacitySensorActor::new);
    }

    private CapacitySensorActor(
            ActorContext<Command> context
    ) {
        super(context);

        getContext().getLog().info(
                "CapacitySensorActor started"
        );
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(CheckCapacity.class, this::onCheckCapacity)
                .build();
    }

    private Behavior<Command> onCheckCapacity(
            CheckCapacity msg
    ) {

        boolean allowed =
                msg.currentItems + msg.addedItems
                        <= msg.maxItems;

        msg.replyTo.tell(
                new CapacityResponse(allowed)
        );

        return Behaviors.same();
    }
}