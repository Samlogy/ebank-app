import 'dotenv/config';

export const config = {
  port: parseInt(process.env.PORT || '3002', 10),
  kafka: {
    brokers: (process.env.KAFKA_BROKERS || 'localhost:9092').split(','),
    groupId: 'notification-group',
    clientId: 'notification-service',
  },
  smtp: {
    host: process.env.SMTP_HOST || 'localhost',
    port: parseInt(process.env.SMTP_PORT || '1025', 10),
    from: process.env.SMTP_FROM || 'noreply@ebank.com',
  },
  twilio: {
    accountSid: process.env.TWILIO_ACCOUNT_SID || '',
    authToken: process.env.TWILIO_AUTH_TOKEN || '',
    fromNumber: process.env.TWILIO_FROM_NUMBER || '',
  },
  redis: {
    host: process.env.REDIS_HOST || 'localhost',
    port: parseInt(process.env.REDIS_PORT || '6379', 10),
    password: process.env.REDIS_PASSWORD || undefined,
    // How long a "message already processed" marker is kept. Must comfortably
    // outlast any realistic Kafka redelivery window (consumer crash + restart,
    // rebalance, etc.) — see docs/caching section for the reasoning.
    dedupeTtlSeconds: parseInt(process.env.NOTIFICATION_DEDUPE_TTL_SECONDS || '86400', 10),
  },
};
