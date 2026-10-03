package example;

public final class OrderWorkflowTest {
  public static void main(String[] args) {
    OrderWorkflow workflow = new OrderWorkflow();
    OrderWorkflow.Decision decision = workflow.decide(new OrderWorkflow.Order("lesson-7", true, false));
    if (!"BACKORDERED".equals(decision.state())) throw new AssertionError(decision);
    System.out.println("OrderWorkflowTest passed: paid order without stock is backordered");
  }
}
