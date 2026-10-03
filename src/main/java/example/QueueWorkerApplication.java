package example;

public final class QueueWorkerApplication {
  public static void main(String[] args) throws Exception {
    String key = System.getenv("INFRAI_API_KEY");
    if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY first");
    InfraiClient client = new InfraiClient(key);
    String payload = "{\"orderId\":\"course-1001\",\"paid\":true,\"stockAvailable\":true}";
    System.out.println("published: " + client.publish(payload, "order-course-1001"));
    System.out.println("consumed: " + client.consume(1, 30));
    System.out.println(new OrderWorkflow().decide(new OrderWorkflow.Order("course-1001", true, true)));
    // Pass the message_id returned by consume to acknowledge it in a real worker.
  }
}
