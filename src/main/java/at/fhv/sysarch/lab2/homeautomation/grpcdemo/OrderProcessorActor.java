package at.fhv.sysarch.lab2.homeautomation.grpcdemo;

import at.fhv.sysarch.lab2.homeautomation.persistence.OrderPersistenceActor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

public class OrderProcessorActor extends AbstractBehavior<OrderProcessorActor.Command> {

    private static final int MAX_AMOUNT = 100;

    public interface Command {}

    public record ProcessOrder(
            String name,
            int    amount,
            double price,
            ActorRef<OrderResult> replyTo
    ) implements Command {}

    public record OrderResult(
            boolean success,
            String  message,
            String  product,
            int     amount,
            double  price,
            double  total
    ) {

        public static OrderResult error(String msg) {
            return new OrderResult(false, msg, "", 0, 0.0, 0.0);
        }
    }

    private final ActorRef<OrderPersistenceActor.Command> persistence;

    public static Behavior<Command> create(ActorRef<OrderPersistenceActor.Command> persistence) {
        return Behaviors.setup(ctx -> new OrderProcessorActor(ctx, persistence));
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

    private Behavior<Command> onProcessOrder(ProcessOrder msg) {
        getContext().getLog().info(
                "Received order: {} x '{}' @ {}", msg.amount(), msg.name(), msg.price()
        );

        // ----- Validation ----------------------------------------------------
        String validationError = validate(msg);
        if (validationError != null) {
            getContext().getLog().warn("Order rejected: {}", validationError);
            if (msg.replyTo() != null) {
                msg.replyTo().tell(OrderResult.error(validationError));
            }
            return this;
        }

        // ----- Persist -------------------------------------------------------
        persistence.tell(new OrderPersistenceActor.SaveOrder(msg.name(), msg.amount()));

        // ----- Build receipt -------------------------------------------------
        double total = msg.amount() * msg.price();

        String receiptMessage = String.format(
                "Receipt: %d x '%s' @ %.2f each = %.2f total",
                msg.amount(), msg.name(), msg.price(), total
        );

        getContext().getLog().info(receiptMessage);

        if (msg.replyTo() != null) {
            msg.replyTo().tell(new OrderResult(
                    true,
                    receiptMessage,
                    msg.name(),
                    msg.amount(),
                    msg.price(),
                    total
            ));
        }

        return this;
    }

    private String validate(ProcessOrder msg) {
        if (msg.name() == null || msg.name().isBlank()) {
            return "Product name must not be empty";
        }
        if (msg.amount() <= 0) {
            return "Amount must be greater than 0 (got " + msg.amount() + ")";
        }
        if (msg.amount() > MAX_AMOUNT) {
            return "Amount exceeds maximum allowed per order (" + MAX_AMOUNT + ")";
        }
        if (msg.price() < 0) {
            return "Unit price must not be negative (got " + msg.price() + ")";
        }
        return null; // all checks passed
    }
}