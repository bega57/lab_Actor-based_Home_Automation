package at.fhv.sysarch.lab2.homeautomation.devices;

import at.fhv.sysarch.lab2.homeautomation.uihandler.OrderHistoryActor;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import at.fhv.sysarch.lab2.homeautomation.grpcdemo.*;
import org.apache.pekko.grpc.GrpcClientSettings;

import java.util.HashMap;
import java.util.Map;


public class Fridge extends AbstractBehavior<Fridge.Command> {

    public interface Command {}

    // ---- sensor check wrappers (per-request correlation id) -----------------
    public record WrappedWeightResponse(
            String requestId, WeightSensorActor.WeightResponse response
    ) implements Command {}

    public record WrappedCapacityResponse(
            String requestId, CapacitySensorActor.CapacityResponse response
    ) implements Command {}

    // ---- public commands ----------------------------------------------------
    public record AddProduct(String name, int amount, double weight, double price) implements Command {}
    public record ConsumeProduct(String name, int amount)                          implements Command {}
    public record GetContents(ActorRef<ContentsResponse> replyTo)                  implements Command {}
    public record ContentsResponse(String contents)                                {}

    public record OrderProduct(String name, int amount, double weight, double price) implements Command {}

    public record WrappedOrderResponse(
            String  name,
            int     amount,
            double  weight,
            double  price,
            boolean success,   // <-- from structured receipt
            String  message,
            double  total      // <-- computed total from receipt
    ) implements Command {}

    public static class ProductEntry {
        private int    amount;
        private double weightPerItem;
        private double pricePerItem;

        public ProductEntry(int amount, double weightPerItem, double pricePerItem) {
            this.amount        = amount;
            this.weightPerItem = weightPerItem;
            this.pricePerItem  = pricePerItem;
        }

        public int    getAmount()      { return amount; }
        public double getWeightPerItem() { return weightPerItem; }
        public double getPricePerItem()  { return pricePerItem; }
        public void   setAmount(int a) { this.amount = a; }

        @Override
        public String toString() {
            return "{amount=" + amount + ", weight=" + weightPerItem + ", price=" + pricePerItem + "}";
        }
    }

    private static final int    MAX_ITEMS     = 20;
    private static final double MAX_WEIGHT    = 20.0;
    private static final int    REORDER_AMOUNT = 5;
    private static final double DEFAULT_WEIGHT = 1.0;
    private static final double DEFAULT_PRICE  = 2.5;

    private final Map<String, ProductEntry> products        = new HashMap<>();
    private double                          currentWeight   = 0;
    private final OrderServiceClient        client;
    private final ActorRef<WeightSensorActor.Command>   weightSensor;
    private final ActorRef<CapacitySensorActor.Command> capacitySensor;
    private final Map<String, PendingRequest>           pendingRequests = new HashMap<>();
    private final ActorRef<OrderHistoryActor.Command>   orderHistory;

    public static Behavior<Command> create(ActorRef<OrderHistoryActor.Command> orderHistory) {
        return Behaviors.setup(context -> {
            OrderServiceClient client = OrderServiceClient.create(
                    GrpcClientSettings.fromConfig(
                            "at.fhv.sysarch.lab2.homeautomation.grpcdemo.OrderService",
                            context.getSystem()
                    ),
                    context.getSystem()
            );

            ActorRef<WeightSensorActor.Command> weightSensor =
                    context.spawn(WeightSensorActor.create(), "weightSensor");

            ActorRef<CapacitySensorActor.Command> capacitySensor =
                    context.spawn(CapacitySensorActor.create(), "capacitySensor");

            return new Fridge(context, client, weightSensor, capacitySensor, orderHistory);
        });
    }

    private Fridge(
            ActorContext<Command> context,
            OrderServiceClient client,
            ActorRef<WeightSensorActor.Command> weightSensor,
            ActorRef<CapacitySensorActor.Command> capacitySensor,
            ActorRef<OrderHistoryActor.Command> orderHistory
    ) {
        super(context);
        this.client        = client;
        this.weightSensor  = weightSensor;
        this.capacitySensor = capacitySensor;
        this.orderHistory  = orderHistory;
        getContext().getLog().info("Fridge started (max {} items, max {} kg)", MAX_ITEMS, MAX_WEIGHT);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(AddProduct.class,              this::onAddProduct)
                .onMessage(ConsumeProduct.class,          this::onConsumeProduct)
                .onMessage(GetContents.class,             this::onGetContents)
                .onMessage(OrderProduct.class,            this::onOrderProduct)
                .onMessage(WrappedOrderResponse.class,    this::onWrappedOrderResponse)
                .onMessage(WrappedWeightResponse.class,   this::onWrappedWeightResponse)
                .onMessage(WrappedCapacityResponse.class, this::onWrappedCapacityResponse)
                .build();
    }

    private Behavior<Command> onAddProduct(AddProduct msg) {
        if (msg.amount() <= 0 || msg.weight() <= 0) {
            getContext().getLog().warn("Invalid product values – ignoring AddProduct");
            return Behaviors.same();
        }

        String requestId = java.util.UUID.randomUUID().toString();
        pendingRequests.put(requestId, new PendingRequest(msg));

        double addedWeight = msg.amount() * msg.weight();
        int    totalItems  = products.values().stream().mapToInt(ProductEntry::getAmount).sum();

        // Adapter actors bridge the sensor response back to Fridge as wrapped messages
        ActorRef<WeightSensorActor.WeightResponse> weightAdapter =
                getContext().spawnAnonymous(Behaviors.receiveMessage(response -> {
                    getContext().getSelf().tell(new WrappedWeightResponse(requestId, response));
                    return Behaviors.stopped();
                }));

        ActorRef<CapacitySensorActor.CapacityResponse> capacityAdapter =
                getContext().spawnAnonymous(Behaviors.receiveMessage(response -> {
                    getContext().getSelf().tell(new WrappedCapacityResponse(requestId, response));
                    return Behaviors.stopped();
                }));

        weightSensor.tell(new WeightSensorActor.CheckWeight(
                currentWeight, addedWeight, MAX_WEIGHT, weightAdapter));

        capacitySensor.tell(new CapacitySensorActor.CheckCapacity(
                totalItems, msg.amount(), MAX_ITEMS, capacityAdapter));

        return Behaviors.same();
    }

    /*
     * Interaction pattern: Per-Session Child Actor
     */
    private Behavior<Command> onOrderProduct(OrderProduct msg) {
        if (msg.amount() <= 0) {
            getContext().getLog().warn("Invalid order amount – ignoring");
            return Behaviors.same();
        }

        getContext().getLog().info("Spawning order session for {} x '{}'", msg.amount(), msg.name());

        // Record the order in history (fire-and-forget)
        orderHistory.tell(new OrderHistoryActor.AddOrder(msg.name(), msg.amount()));

        // Spawn a per-session child actor for this single order
        ActorRef<OrderSessionActor.Command> session = getContext().spawnAnonymous(
                OrderSessionActor.create(
                        client,
                        getContext().getSelf(),
                        msg.name(), msg.amount(), msg.weight(), msg.price()
                )
        );

        // Kick off the session immediately
        session.tell(new OrderSessionActor.Start());

        return Behaviors.same();
    }

    private Behavior<Command> onWrappedOrderResponse(WrappedOrderResponse msg) {
        if (!msg.success()) {
            getContext().getLog().error(
                    "Order for '{}' failed: {}", msg.name(), msg.message()
            );
            return Behaviors.same();
        }

        getContext().getLog().info(
                "Order receipt received – {} | total: {}", msg.message(), msg.total()
        );

        // Add the ordered items to the fridge (triggers weight/capacity check)
        getContext().getSelf().tell(
                new AddProduct(msg.name(), msg.amount(), msg.weight(), msg.price())
        );

        return Behaviors.same();
    }

    private Behavior<Command> onConsumeProduct(ConsumeProduct msg) {
        if (msg.amount() <= 0) {
            getContext().getLog().warn("Invalid consume amount");
            return Behaviors.same();
        }

        ProductEntry entry = products.get(msg.name());
        if (entry == null || entry.getAmount() < msg.amount()) {
            getContext().getLog().warn("Not enough '{}' in fridge!", msg.name());
            return Behaviors.same();
        }

        entry.setAmount(entry.getAmount() - msg.amount());
        currentWeight = Math.max(0, currentWeight - msg.amount() * entry.getWeightPerItem());

        getContext().getLog().info("Consumed {} x '{}'", msg.amount(), msg.name());

        // Auto-reorder when the last unit is consumed
        if (entry.getAmount() == 0) {
            double reorderWeight = entry.getWeightPerItem();
            double reorderPrice  = entry.getPricePerItem();
            products.remove(msg.name());

            getContext().getLog().info("'{}' empty – auto-reordering {}x", msg.name(), REORDER_AMOUNT);

            getContext().getSelf().tell(
                    new OrderProduct(msg.name(), REORDER_AMOUNT, reorderWeight, reorderPrice)
            );
        }

        return Behaviors.same();
    }

    private Behavior<Command> onGetContents(GetContents msg) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (var entry : products.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":")
                    .append(entry.getValue().getAmount());
            first = false;
        }
        json.append("}");
        msg.replyTo().tell(new ContentsResponse(json.toString()));
        return Behaviors.same();
    }

    private Behavior<Command> onWrappedWeightResponse(WrappedWeightResponse msg) {
        PendingRequest pending = pendingRequests.get(msg.requestId());
        if (pending == null) return Behaviors.same();

        if (!msg.response().allowed()) {
            getContext().getLog().warn("Weight limit exceeded – order rejected");
            pendingRequests.remove(msg.requestId());
            return Behaviors.same();
        }

        pending.setWeightOk(true);
        tryAddProduct(msg.requestId());
        return Behaviors.same();
    }

    private Behavior<Command> onWrappedCapacityResponse(WrappedCapacityResponse msg) {
        PendingRequest pending = pendingRequests.get(msg.requestId());
        if (pending == null) return Behaviors.same();

        if (!msg.response().allowed()) {
            getContext().getLog().warn("Capacity limit exceeded – order rejected");
            pendingRequests.remove(msg.requestId());
            return Behaviors.same();
        }

        pending.setCapacityOk(true);
        tryAddProduct(msg.requestId());
        return Behaviors.same();
    }

    private void tryAddProduct(String requestId) {
        PendingRequest pending = pendingRequests.get(requestId);
        if (pending == null || !pending.isWeightOk() || !pending.isCapacityOk()) return;

        AddProduct req   = pending.getRequest();
        ProductEntry entry = products.getOrDefault(
                req.name(), new ProductEntry(0, req.weight(), req.price())
        );
        entry.setAmount(entry.getAmount() + req.amount());
        products.put(req.name(), entry);
        currentWeight += req.amount() * req.weight();

        getContext().getLog().info(
                "Added {} x '{}' (total now: {})", req.amount(), req.name(), entry.getAmount()
        );

        pendingRequests.remove(requestId);
    }

    private static class PendingRequest {
        private final AddProduct request;
        private boolean weightOk   = false;
        private boolean capacityOk = false;

        PendingRequest(AddProduct request) { this.request = request; }

        public AddProduct getRequest()              { return request; }
        public boolean    isWeightOk()              { return weightOk; }
        public boolean    isCapacityOk()            { return capacityOk; }
        public void       setWeightOk(boolean v)    { weightOk = v; }
        public void       setCapacityOk(boolean v)  { capacityOk = v; }
    }
}