package at.fhv.sysarch.lab2.homeautomation.devices;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.grpcdemo.*;
import org.apache.pekko.grpc.GrpcClientSettings;

import java.util.HashMap;
import java.util.Map;

public class Fridge extends AbstractBehavior<Fridge.Command> {

    public interface Command {}

    public record AddProduct(String name, int amount, double weight) implements Command {}
    public record ConsumeProduct(String name, int amount) implements Command {}
    public record GetContents() implements Command {}
    public record OrderProduct(String name, int amount) implements Command {}

    public static class ProductEntry {
        public int amount;
        public double weightPerItem;

        public ProductEntry(int amount, double weightPerItem) {
            this.amount = amount;
            this.weightPerItem = weightPerItem;
        }

        @Override
        public String toString() {
            return "{amount=" + amount + ", weight=" + weightPerItem + "}";
        }
    }

    private final Map<String, ProductEntry> products = new HashMap<>();
    private final int maxItems = 10;
    private final double maxWeight = 20.0;
    private double currentWeight = 0;
    private final OrderServiceClient client;

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> {

            OrderServiceClient client = OrderServiceClient.create(
                    GrpcClientSettings.fromConfig("at.fhv.sysarch.lab2.homeautomation.grpcdemo.OrderService", context.getSystem()),
                    context.getSystem()
            );

            return new Fridge(context, client);
        });
    }

    private Fridge(ActorContext<Command> context, OrderServiceClient client) {
        super(context);
        this.client = client;
        getContext().getLog().info("Fridge started");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(AddProduct.class, this::onAddProduct)
                .onMessage(ConsumeProduct.class, this::onConsumeProduct)
                .onMessage(GetContents.class, this::onGetContents)
                .onMessage(OrderProduct.class, this::onOrderProduct)
                .build();
    }

    private Behavior<Command> onAddProduct(AddProduct msg) {

        double addedWeight = msg.amount * msg.weight;

        int totalItems = products.values().stream().mapToInt(p -> p.amount).sum();

        if (totalItems + msg.amount > maxItems) {
            getContext().getLog().info("Not enough space in fridge!");
            return this;
        }

        if (currentWeight + addedWeight > maxWeight) {
            getContext().getLog().info("Too heavy!");
            return this;
        }

        ProductEntry entry = products.getOrDefault(
                msg.name,
                new ProductEntry(0, msg.weight)
        );

        entry.amount += msg.amount;
        products.put(msg.name, entry);

        currentWeight += addedWeight;

        getContext().getLog().info("Added {} x {}", msg.amount, msg.name);

        return this;
    }

    private Behavior<Command> onOrderProduct(OrderProduct msg) {
        getContext().getLog().info("Ordering {} x {}", msg.amount, msg.name);

        return onAddProduct(new AddProduct(msg.name, msg.amount, 1.0));
    }

    private Behavior<Command> onConsumeProduct(ConsumeProduct msg) {
        ProductEntry entry = products.get(msg.name);

        if (entry == null || entry.amount < msg.amount) {
            getContext().getLog().info("Not enough {} in fridge!", msg.name);
            return this;
        }

        entry.amount -= msg.amount;
        currentWeight -= msg.amount * entry.weightPerItem;

        getContext().getLog().info("Consumed {} x {}", msg.amount, msg.name);

        if (entry.amount == 0) {
            getContext().getLog().info("{} is empty → ordering more!", msg.name);

            OrderRequest request = OrderRequest.newBuilder()
                    .setName(msg.name)
                    .setAmount(5)
                    .build();

            client.orderProduct(request)
                    .thenAccept(response -> {
                        System.out.println("gRPC Response: " + response.getMessage());
                        getContext().getSelf().tell(new OrderProduct(msg.name, 5));
                    })
                    .exceptionally(error -> {
                        getContext().getLog().error("gRPC Error: {}", error.getMessage());
                        return null;
                    });
        }

        return this;
    }

    private Behavior<Command> onGetContents(GetContents msg) {
        getContext().getLog().info("Fridge contents: {}", products);
        return this;
    }
}

