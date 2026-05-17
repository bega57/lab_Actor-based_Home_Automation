package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.grpcdemo.OrderRequest;
import at.fhv.sysarch.lab2.homeautomation.grpcdemo.OrderResponse;
import at.fhv.sysarch.lab2.homeautomation.grpcdemo.OrderServiceClient;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;


public class OrderSessionActor extends AbstractBehavior<OrderSessionActor.Command> {

    public interface Command {}

    /** Triggers the actual gRPC call – sent by Fridge immediately after spawning. */
    public static class Start implements Command {}

    /** Internal: gRPC call succeeded. */
    private record GrpcSuccess(OrderResponse response) implements Command {}

    /** Internal: gRPC call failed. */
    private record GrpcFailure(String errorMessage)    implements Command {}

    private final OrderServiceClient       client;
    private final ActorRef<Fridge.Command> fridge;

    // Order data needed to build the reply
    private final String name;
    private final int    amount;
    private final double weight;
    private final double price;

    public static Behavior<Command> create(
            OrderServiceClient       client,
            ActorRef<Fridge.Command> fridge,
            String name, int amount, double weight, double price
    ) {
        return Behaviors.setup(ctx ->
                new OrderSessionActor(ctx, client, fridge, name, amount, weight, price)
        );
    }

    private OrderSessionActor(
            ActorContext<Command>    context,
            OrderServiceClient       client,
            ActorRef<Fridge.Command> fridge,
            String name, int amount, double weight, double price
    ) {
        super(context);
        this.client  = client;
        this.fridge  = fridge;
        this.name    = name;
        this.amount  = amount;
        this.weight  = weight;
        this.price   = price;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Start.class,      this::onStart)
                .onMessage(GrpcSuccess.class, this::onGrpcSuccess)
                .onMessage(GrpcFailure.class, this::onGrpcFailure)
                .build();
    }


    private Behavior<Command> onStart(Start msg) {
        getContext().getLog().info(
                "OrderSession started – ordering {} x '{}' @ {}", amount, name, price
        );

        OrderRequest request = OrderRequest.newBuilder()
                .setName(name)
                .setAmount(amount)
                .setPrice(price)   // unit price forwarded so server can compute total
                .build();

        // pipeToSelf: safely brings the CompletionStage result back into the actor
        // thread instead of accessing actor state from an external callback thread.
        getContext().pipeToSelf(
                client.orderProduct(request),
                (response, error) -> {
                    if (error != null) {
                        return new GrpcFailure(error.getMessage());
                    }
                    return new GrpcSuccess(response);
                }
        );

        return Behaviors.same();
    }

    private Behavior<Command> onGrpcSuccess(GrpcSuccess msg) {
        OrderResponse resp = msg.response();

        getContext().getLog().info(
                "Order confirmed – {}", resp.getMessage()
        );

        fridge.tell(new Fridge.WrappedOrderResponse(
                name, amount, weight, price,
                resp.getSuccess(),
                resp.getMessage(),
                resp.getTotal()
        ));

        // Session is done – stop this actor
        return Behaviors.stopped();
    }

    private Behavior<Command> onGrpcFailure(GrpcFailure msg) {
        getContext().getLog().error(
                "gRPC call failed for order '{}': {}", name, msg.errorMessage()
        );

        fridge.tell(new Fridge.WrappedOrderResponse(
                name, amount, weight, price,
                false,
                "gRPC error: " + msg.errorMessage(),
                0.0
        ));

        return Behaviors.stopped();
    }
}