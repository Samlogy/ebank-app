import { redis } from './redis.client';
import { config } from '../config';
import { logger } from '../logger';

const KEY_PREFIX = 'notif:dedup:';

/**
 * Marks a Kafka message as processed, atomically, the first time it's seen.
 *
 * WHY: KafkaJS's default `eachMessage` auto-commits offsets after the handler
 * resolves. If the process crashes (or a rebalance happens) between sending a
 * notification and the offset commit landing, Kafka redelivers the message on
 * restart — at-least-once delivery. Email/SMS/push sends are not idempotent
 * operations, so without a guard the customer gets the same "your transfer of
 * $500 completed" email/SMS twice.
 *
 * Returns true the first time a given key is seen (caller should process the
 * message), false if it's a redelivery (caller should skip it).
 *
 * Uses `SET key 1 EX ttl NX` — a single atomic Redis command, so concurrent
 * consumers in the same group can never both win the race for the same key.
 *
 * Fails OPEN on Redis errors: if Redis is unreachable, this returns true
 * (process the message) rather than throwing. A missed notification is worse
 * for this service than an occasional duplicate — this is a notification
 * service, not the ledger — so we trade a small chance of a duplicate email
 * for never silently dropping one because the cache was down.
 */
export async function markIfNew(key: string, ttlSeconds: number = config.redis.dedupeTtlSeconds): Promise<boolean> {
  try {
    const result = await redis.set(KEY_PREFIX + key, '1', 'EX', ttlSeconds, 'NX');
    return result === 'OK';
  } catch (err) {
    logger.warn({ err, key }, '[DEDUPE] Redis unavailable, processing message without a dedupe guard');
    return true;
  }
}
