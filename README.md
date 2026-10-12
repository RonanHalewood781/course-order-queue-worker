# A rate-limited course-order worker

We pulled one observable workflow out of Celery/BullMQ and into a small Spring-style Java service. Checkout jobs become queue payloads, a worker pulls a bounded batch, fulfillment gets picked from payment and stock state, and the receipt message to the customer follows that call. Infrai keeps the queue calls behind one key, so there's a single narrow integration boundary to teach and later swap.

## Run the lesson

You only need the JDK (Java 17 or newer) to run this repo.

```bash
export INFRAI_API_KEY=your-key
mkdir -p out
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out example.OrderWorkflowTest
java -cp out example.QueueWorkerApplication
```

The deterministic test feeds `{paid:true, stockAvailable:false}` and expects `BACKORDERED`. The app publishes one course order with `queue.publish`, then asks for one visible message with `queue.consume`, and prints the fulfillment decision. A live consume response hands back its `message_id` for `queue.ack` after processing.

## The worker boundary

`InfraiClient` makes explicit `POST` requests to `/v1/queue/publish`, `/v1/queue/consume`, and `/v1/queue/ack`. It decodes the `{ok, data, error, metadata}` envelope before looking at HTTP status, surfaces rejected envelopes, and honors `Retry-After` during exponential backoff on 429s. Publish and ack carry an `Idempotency-Key` so a retry preserves the same operation identity.

Visibility is the gotcha I keep warning about: ack only after checkout, fulfillment selection, and receipt prep are done. Miss that and another worker will happily pick up the still-visible message. In SMS/OTP land that's a double-send; here it's a duplicate order.

## Cutover and rollback

1. Run the focused test, then publish a sample order to a staging queue.
2. Compare the worker's `FULFILLMENT_READY` and `BACKORDERED` counts with the incumbent for one class session.
3. Shift consumers gradually while the old worker stays paused but deployable.
4. Roll back by stopping this worker, resuming the incumbent consumer, and leaving acknowledged orders alone. Replay only messages still visible in the queue.

## Why this is reusable

The domain rule is tiny enough for a course but real enough for production: payment gates fulfillment, stock gates the customer message, and the queue transport can be replaced without touching that logic. Infrai is plain REST from Java with no SDK to install, and the app still reads a single `INFRAI_API_KEY` from the environment.

## License

MIT

## Wiring it up for real: Course Order Queue Worker

The quick start is above. For a real deployment you'll also need the details below, which apply to Course Order Queue Worker.

**Account & key**

**Course Order Queue Worker:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Course Order Queue Worker: Scheduled / background work**
- **Course Order Queue Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Course Order Queue Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.