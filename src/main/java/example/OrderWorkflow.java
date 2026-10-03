package example;

public final class OrderWorkflow {
  public record Order(String orderId, boolean paid, boolean stockAvailable) {}
  public record Decision(String state, String customerMessage) {}

  public Decision decide(Order order) {
    if (!order.paid()) return new Decision("PAYMENT_REQUIRED", "Checkout needs payment");
    if (!order.stockAvailable()) return new Decision("BACKORDERED", "We will email you when stock returns");
    return new Decision("FULFILLMENT_READY", "Order accepted for fulfillment");
  }
}
