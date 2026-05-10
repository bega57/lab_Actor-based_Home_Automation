package at.fhv.sysarch.lab2.homeautomation.grpcdemo;

import at.fhv.sysarch.lab2.homeautomation.persistence.OrderPersistenceActor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class OrderProcessorActor extends AbstractBehavior<OrderProcessorActor.Command> {

    public interface Command {}

    public record ProcessOrder(
            String name,
            int amount,
            ActorRef<OrderResult> replyTo
    ) implements Command {}

    public record OrderResult(
            boolean success,
            String message
    ) {}

    private final ActorRef<OrderPersistenceActor.Command> persistence;

    public static Behavior<Command> create(
            ActorRef<OrderPersistenceActor.Command> persistence
    ) {
        return Behaviors.setup(
                ctx -> new OrderProcessorActor(ctx, persistence)
        );
    }

    private OrderProcessorActor(
            ActorContext<Command> context,
            ActorRef<OrderPersistenceActor.Command> persistence
    ) {

        super(context);

        this.persistence = persistence;
    }

    @Override
    public Receive<Command> createReceive() {

        return newReceiveBuilder()
                .onMessage(ProcessOrder.class, this::onProcessOrder)
                .build();
    }

    private Behavior<Command> onProcessOrder(
            ProcessOrder msg
    ) {

        getContext().getLog().info(
                "PROCESSING ORDER: {} x {}",
                msg.amount,
                msg.name
        );

        if (msg.amount <= 0) {

            if (msg.replyTo != null) {

                msg.replyTo.tell(
                        new OrderResult(
                                false,
                                "Invalid amount"
                        )
                );
            }

            return this;
        }

        persistence.tell(
                new OrderPersistenceActor.SaveOrder(
                        msg.name,
                        msg.amount
                )
        );

        if (msg.replyTo != null) {

            msg.replyTo.tell(
                    new OrderResult(
                            true,
                            "Order successful: "
                                    + msg.amount
                                    + " x "
                                    + msg.name
                    )
            );
        }

        return this;
    }
}