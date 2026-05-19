package at.fhv.sysarch.lab2.homeautomation.persistence;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderPersistenceActor
        extends EventSourcedBehavior<
        OrderPersistenceActor.Command,
        OrderPersistenceActor.Event,
        OrderPersistenceActor.State> {

    // ── Commands ──────────────────────────────────────────────────────
    public interface Command {}

    public record SaveOrder(String product, int amount) implements Command {}

    public record GetOrders(ActorRef<OrderResponse> replyTo) implements Command {}
    public record OrderResponse(String history) {}

    public record ClearOrders() implements Command {}

    // ── Events ────────────────────────────────────────────────────────
    public interface Event extends Serializable {}

    public record OrderSaved(String product, int amount) implements Event {
        private static final long serialVersionUID = 1L;
    }

    // ── State ─────────────────────────────────────────────────────────
    public static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<String> orders;

        public State() {
            this.orders = new ArrayList<>();
        }

        private State(List<String> orders) {
            this.orders = orders;
        }

        public State addOrder(String product, int amount) {
            List<String> next = new ArrayList<>(orders);
            next.add("{\"product\":\"" + product + "\",\"amount\":" + amount + "}");
            return new State(next);
        }

        public List<String> getOrders() {
            return Collections.unmodifiableList(orders);
        }
    }

    // ── Context ───────────────────────────────────────────────────────
    private final ActorContext<Command> ctx;

    // ── Factory ───────────────────────────────────────────────────────
    public static Behavior<Command> create() {
        return Behaviors.setup(OrderPersistenceActor::new);
    }

    private OrderPersistenceActor(ActorContext<Command> ctx) {
        super(PersistenceId.ofUniqueId("order-persistence-v1"));
        this.ctx = ctx;
        ctx.getLog().info("OrderPersistenceActor started (event-sourced)");
    }

    // ── EventSourcedBehavior ──────────────────────────────────────────
    @Override
    public State emptyState() {
        return new State();
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(SaveOrder.class, (state, cmd) -> {
                    ctx.getLog().info(
                            "Persisting order: {} x '{}'", cmd.amount(), cmd.product()
                    );
                    return Effect()
                            .persist(new OrderSaved(cmd.product(), cmd.amount()))
                            .thenRun(s -> ctx.getLog().info(
                                    "Persisted – journal has {} entries", s.getOrders().size()
                            ));
                })
                .onCommand(GetOrders.class, (state, cmd) -> {
                    String json = "[" + String.join(",", state.getOrders()) + "]";
                    cmd.replyTo().tell(new OrderResponse(json));
                    return Effect().none();
                })
                .onCommand(ClearOrders.class, (state, cmd) -> {
                    ctx.getLog().warn("ClearOrders ignored – journal is append-only");
                    return Effect().none();
                })
                .build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(OrderSaved.class, (state, evt) ->
                        state.addOrder(evt.product(), evt.amount())
                )
                .build();
    }
}