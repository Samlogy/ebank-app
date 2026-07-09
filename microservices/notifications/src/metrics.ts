import client from 'prom-client';

// Default Node.js process metrics (event loop lag, heap, GC, etc.) — same
// low-effort baseline Micrometer gives the Java services for free.
export const registry = new client.Registry();
client.collectDefaultMetrics({
  register: registry,
  prefix: 'notification_service_',
});

export const kafkaMessagesConsumedTotal = new client.Counter({
  name: 'kafka_messages_consumed_total',
  help: 'Kafka messages consumed, by topic and outcome',
  labelNames: ['topic', 'status'] as const,
  registers: [registry],
});

export const notificationsSentTotal = new client.Counter({
  name: 'notifications_sent_total',
  help: 'Notifications sent, by channel and outcome',
  labelNames: ['channel', 'status'] as const,
  registers: [registry],
});

export const notificationSendDurationSeconds = new client.Histogram({
  name: 'notification_send_duration_seconds',
  help: 'Time spent sending a notification, by channel',
  labelNames: ['channel'] as const,
  buckets: [0.01, 0.05, 0.1, 0.25, 0.5, 1, 2, 5],
  registers: [registry],
});
