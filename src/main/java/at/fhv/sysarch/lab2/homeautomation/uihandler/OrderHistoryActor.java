package at.fhv.sysarch.lab2.homeautomation.uihandler;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

import java.util.ArrayList;
import java.util.List;

public class OrderHistoryActor extends AbstractBehavior<OrderHistoryActor.Command> {

    public interface Command {}

    public record AddOrder(
            String product,
            int amount
    ) implements Command {}

    public record GetHistory(
            ActorRef<String> replyTo
    ) implements Command {}

    public record ClearHistory() implements Command {}

    private final List<String> history = new ArrayList<>();

    public static Behavior<Command> create() {

        return Behaviors.setup(
                OrderHistoryActor::new
        );
    }

    private OrderHistoryActor(
            ActorContext<Command> context
    ) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {

        return newReceiveBuilder()
                .onMessage(AddOrder.class, this::onAddOrder)
                .onMessage(GetHistory.class, this::onGetHistory)
                .onMessage(ClearHistory.class, this::onClearHistory)
                .build();
    }

    private Behavior<Command> onAddOrder(
            AddOrder msg
    ) {

        history.add(
                "{\"product\":\""
                        + msg.product()
                        + "\",\"amount\":"
                        + msg.amount()
                        + "}"
        );

        return Behaviors.same();
    }

    private Behavior<Command> onGetHistory(
            GetHistory msg
    ) {

        String json =
                "[" + String.join(",", history) + "]";

        msg.replyTo.tell(json);

        return Behaviors.same();
    }

    private Behavior<Command> onClearHistory(
            ClearHistory msg
    ) {

        history.clear();

        return Behaviors.same();
    }
}