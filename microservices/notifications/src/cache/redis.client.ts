import Redis from 'ioredis';
import { config } from '../config';
import { logger } from '../logger';

// Single shared connection — this service only ever does simple key
// set/get operations, no pub/sub or blocking commands, so one client
// is enough.
export const redis = new Redis({
  host: config.redis.host,
  port: config.redis.port,
  password: config.redis.password,
  // Fail fast on individual commands rather than queueing indefinitely —
  // if Redis is unreachable we want the idempotency check to error out
  // quickly so the caller can fail open (see idempotency.service.ts).
  maxRetriesPerRequest: 3,
  lazyConnect: false,
});

redis.on('error', (err) => {
  logger.error({ err }, '[REDIS] Connection error');
});

redis.on('connect', () => {
  logger.info('[REDIS] Connected');
});
