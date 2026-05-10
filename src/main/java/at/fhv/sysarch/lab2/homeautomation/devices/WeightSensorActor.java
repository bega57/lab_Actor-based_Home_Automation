package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class WeightSensorActor extends AbstractBehavior<WeightSensorActor.Command> {

    public interface Command {}

    public record CheckWeight(
            double currentWeight,
            double addedWeight,
            double maxWeight,
            ActorRef<WeightResponse> replyTo
    ) implements Command {}

    public record WeightResponse(boolean allowed) {}

    public static Behavior<Command> create() {
        return Behaviors.setup(WeightSensorActor::new);
    }

    private WeightSensorActor(
            ActorContext<Command> context
    ) {
        super(context);

        getContext().getLog().info(
                "WeightSensorActor started"
        );
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(CheckWeight.class, this::onCheckWeight)
                .build();
    }

    private Behavior<Command> onCheckWeight(
            CheckWeight msg
    ) {

        boolean allowed =
                msg.currentWeight + msg.addedWeight
                        <= msg.maxWeight;

        msg.replyTo.tell(
                new WeightResponse(allowed)
        );

        return Behaviors.same();
    }
}