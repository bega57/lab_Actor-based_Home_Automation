package at.fhv.sysarch.lab2.homeautomation.grpcdemo;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class OrderServiceImpl implements OrderService {

    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(5);

    private final ActorRef<OrderProcessorActor.Command> processor;
    private final ActorSystem<?>                        system;

    public OrderServiceImpl(
            ActorRef<OrderProcessorActor.Command> processor,
            ActorSystem<?>                        system
    ) {
        this.processor = processor;
        this.system    = system;
    }

    @Override
    public CompletionStage<OrderResponse> orderProduct(OrderRequest request) {
        return AskPattern
                .<OrderProcessorActor.Command, OrderProcessorActor.OrderResult>ask(
                        processor,
                        replyTo -> new OrderProcessorActor.ProcessOrder(
                                request.getName(),
                                request.getAmount(),
                                request.getPrice(),   // <-- price forwarded from proto request
                                replyTo
                        ),
                        ASK_TIMEOUT,
                        system.scheduler()
                )
                .thenApply(result -> OrderResponse.newBuilder()
                        .setSuccess(result.success())
                        .setMessage(result.message())
                        .setProduct(result.product())
                        .setAmount(result.amount())
                        .setPrice(result.price())
                        .setTotal(result.total())
                        .build()
                );
    }
}