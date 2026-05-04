package at.fhv.sysarch.lab2.homeautomation.grpcdemo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class OrderServiceImpl implements OrderService {

    @Override
    public CompletionStage<OrderResponse> orderProduct(OrderRequest request) {

        String name = request.getName();
        int amount = request.getAmount();

        System.out.println("Processing order: " + amount + " x " + name);

        OrderResponse response = OrderResponse.newBuilder()
                .setMessage("Order successful: " + amount + " x " + name)
                .build();

        return CompletableFuture.completedFuture(response);
    }
}