import 'dotenv/config';
import express, { Request, Response } from 'express';
import { config } from './config';
import { logger } from './logger';
import { startKafkaConsumer, stopKafkaConsumer } from './consumer/kafka.consumer';

// ---------------------------------------------------------------------------
// Express app
// ---------------------------------------------------------------------------

const app = express();
app.use(express.json());

app.get('/health', (_req: Request, res: Response) => {
  res.json({ status: 'UP', service: 'notification-service' });
});

app.get('/', (_req: Request, res: Response) => {
  res.json({ service: 'eBank Notification Service', version: '1.0.0' });
});

// ---------------------------------------------------------------------------
// Kafka bootstrap with retry
// ---------------------------------------------------------------------------

const MAX_KAFKA_RETRIES = 10;
const KAFKA_RETRY_INTERVAL_MS = 5_000;

async function connectKafkaWithRetry(attempt = 1): Promise<void> {
  try {
    await startKafkaConsumer();
  } catch (err) {
    if (attempt >= MAX_KAFKA_RETRIES) {
      logger.error({ err }, `Kafka: could not connect after ${MAX_KAFKA_RETRIES} attempts. Giving up.`);
      return;
    }
    logger.warn(
      { attempt, maxRetries: MAX_KAFKA_RETRIES, err: (err as Error).message },
      `Kafka: connection attempt ${attempt}/${MAX_KAFKA_RETRIES} failed. Retrying in ${KAFKA_RETRY_INTERVAL_MS / 1000}s…`,
    );
    await new Promise<void>((resolve) => setTimeout(resolve, KAFKA_RETRY_INTERVAL_MS));
    await connectKafkaWithRetry(attempt + 1);
  }
}

// ---------------------------------------------------------------------------
// Graceful shutdown
// ---------------------------------------------------------------------------

async function shutdown(signal: string): Promise<void> {
  logger.info({ signal }, 'Received signal. Shutting down gracefully…');
  try {
    await stopKafkaConsumer();
  } catch (err) {
    logger.error({ err }, 'Error during Kafka shutdown');
  }
  process.exit(0);
}

process.on('SIGTERM', () => void shutdown('SIGTERM'));
process.on('SIGINT', () => void shutdown('SIGINT'));

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------

const server = app.listen(config.port, () => {
  logger.info({ port: config.port }, 'Notification service started');
});

// Fire-and-forget Kafka connection (retries handled internally)
void connectKafkaWithRetry();

export { app, server };
