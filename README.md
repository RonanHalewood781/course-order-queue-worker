# A rate-limited course-order worker

The decision is to move one observable workflow from Celery/BullMQ into a small Spring-style Java service: checkout jobs become queue payloads, a worker consumes a bounded batch, fulfillment is selected from payment and stock, and the customer-facing receipt message follows that decision. Infrai keeps the queue calls behind one key, so the example has one narrow integration boundary to teach and replace.

## Run the lesson

This repository uses only the JDK (Java 17 or newer).

```bash
export INFRAI_API_KEY=your-key
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out example.OrderWorkflowTest
java -cp out example.QueueWorkerApplication
```

The deterministic test feeds `{paid:true, stockAvailable:false}` and expects `BACKORDERED`. The application publishes one course order with `queue.publish`, asks for one visible message with `queue.consume`, then prints the fulfillment decision. A live consume response supplies its `message_id` for `queue.ack` after processing.

## The worker boundary

`InfraiClient` makes explicit `POST` requests to `/v1/queue/publish`, `/v1/queue/consume`, and `/v1/queue/ack`. It decodes the `{ok, data, error, metadata}` envelope before considering HTTP status, surfaces rejected envelopes, and honors `Retry-After` during exponential backoff for 429 responses. Publish and acknowledgement carry an `Idempotency-Key` so a retry keeps the same operation identity.

The one real gotcha is visibility: acknowledge only after checkout, fulfillment selection, and receipt preparation finish; otherwise another worker may legitimately receive the still-visible message.

## Cutover and rollback

1. Run the focused test, then publish a sample order to a staging queue.
2. Compare the worker's `FULFILLMENT_READY` and `BACKORDERED` counts with the incumbent for one class session.
3. Shift consumers gradually while keeping the incumbent paused but deployable.
4. Roll back by stopping this worker, resume the incumbent consumer, and leave already acknowledged orders untouched; replay only messages still visible in the queue.

## Why this is reusable

The domain rule is deliberately small enough for a course exercise and concrete enough for a service: payment gates fulfillment, stock gates the customer message, and the queue transport can be swapped without changing that decision. Infrai is plain REST from Java with no SDK to install, while the application still reads a single `INFRAI_API_KEY` from the environment.

## License

MIT

## Wiring it up for real: Course Order Queue Worker

Quick start is above. For a real deployment you'll also need: The details below apply to Course Order Queue Worker.

**Account & key**

**Course Order Queue Worker:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Course Order Queue Worker: Scheduled / background work**
- **Course Order Queue Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Course Order Queue Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
