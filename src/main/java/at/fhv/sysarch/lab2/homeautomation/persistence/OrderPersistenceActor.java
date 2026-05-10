package at.fhv.sysarch.lab2.homeautomation.persistence;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class OrderPersistenceActor extends AbstractBehavior<OrderPersistenceActor.Command> {

    public interface Command {}

    public record SaveOrder(String product, int amount) implements Command {}
    public record GetOrders(ActorRef<OrderResponse> replyTo) implements Command {}
    public record OrderResponse(String history) {}
    public record ClearOrders() implements Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup(OrderPersistenceActor::new);
    }

    private Connection connection;

    private OrderPersistenceActor(
            ActorContext<Command> context
    ) {

        super(context);

        try {

            connection = DriverManager.getConnection(
                    "jdbc:h2:./ordersdb"
            );

            connection.createStatement().execute(
                    """
                    CREATE TABLE IF NOT EXISTS orders (
                        id IDENTITY PRIMARY KEY,
                        product VARCHAR(255),
                        amount INT
                    )
                    """
            );

            getContext().getLog().info(
                    "H2 database connected"
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    @Override
    public Receive<Command> createReceive() {

        return newReceiveBuilder()
                .onMessage(SaveOrder.class, this::onSaveOrder)
                .onMessage(GetOrders.class, this::onGetOrders)
                .onMessage(ClearOrders.class, this::onClearOrders)
                .build();
    }

    private Behavior<Command> onSaveOrder(
            SaveOrder msg
    ) {

        try {

            PreparedStatement stmt =
                    connection.prepareStatement(
                            "INSERT INTO orders(product, amount) VALUES (?, ?)"
                    );

            stmt.setString(1, msg.product);
            stmt.setInt(2, msg.amount);

            stmt.executeUpdate();

            getContext().getLog().info(
                    "Order persisted: {} x {}",
                    msg.amount,
                    msg.product
            );

        } catch (Exception e) {

            e.printStackTrace();
        }

        return this;
    }

    private Behavior<Command> onGetOrders(
            GetOrders msg
    ) {

        try {

            ResultSet rs =
                    connection.createStatement().executeQuery(
                            "SELECT * FROM orders"
                    );

            StringBuilder json = new StringBuilder("[");

            boolean first = true;

            while (rs.next()) {

                if (!first) {
                    json.append(",");
                }

                json.append("{")
                        .append("\"product\":\"")
                        .append(rs.getString("product"))
                        .append("\",")

                        .append("\"amount\":")
                        .append(rs.getInt("amount"))

                        .append("}");

                first = false;
            }

            json.append("]");

            msg.replyTo.tell(
                    new OrderResponse(
                            json.toString()
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();
        }

        return this;
    }

    private Behavior<Command> onClearOrders(
            ClearOrders msg
    ) {

        try {

            connection.createStatement().executeUpdate(
                    "DELETE FROM orders"
            );

            getContext().getLog().info(
                    "All orders deleted"
            );

        } catch (Exception e) {

            e.printStackTrace();
        }

        return this;
    }
}