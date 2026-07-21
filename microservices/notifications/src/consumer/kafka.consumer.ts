import { Kafka, Consumer, KafkaMessage, EachMessagePayload } from 'kafkajs';
import { config } from '../config';
import { TransactionEvent, NotificationEvent } from '../types/events';
import { sendEmail, buildTransactionEmail } from '../services/email.service';
import { sendSms, buildTransactionSms } from '../services/sms.service';
import {
  sendPushNotification,
  buildTransactionPush,
} from '../services/push.service';
import { kafkaMessagesConsumedTotal } from '../metrics';

const TOPICS = {
  TRANSACTION_EVENTS: 'transaction-events',
  NOTIFICATION_EVENTS: 'notification-events',
} as const;

// ---------------------------------------------------------------------------
// Kafka client & consumer
// ---------------------------------------------------------------------------

const kafka = new Kafka({
  clientId: config.kafka.clientId,
  brokers: config.kafka.brokers,
  retry: {
    initialRetryTime: 300,
    retries: 10,
  },
});

let consumer: Consumer | null = null;

// ---------------------------------------------------------------------------
// Topic handlers
// ---------------------------------------------------------------------------

async function handleTransactionEvent(raw: KafkaMessage): Promise<void> {
  if (!raw.value) return;

  const event: TransactionEvent = JSON.parse(raw.value.toString()) as TransactionEvent;

  console.log(
    `[KAFKA] transaction-events | type=${event.type} status=${event.status} ` +
      `amount=${event.amount} txId=${event.transactionId}`,
  );

  // Derive a mock recipient email from the relevant accountId
  const accountId = event.fromAccountId ?? event.toAccountId ?? 'unknown';
  const recipientEmail = `account-${accountId}@ebank-users.local`;

  // Email
  const { subject, body } = buildTransactionEmail(event);
  try {
    await sendEmail(recipientEmail, subject, body);
  } catch (err) {
    console.error('[EMAIL] Failed to send transaction email:', err);
  }

  // SMS (mock phone derived from accountId)
  const mockPhone = `+1555${accountId.replace(/\D/g, '').slice(0, 7).padEnd(7, '0')}`;
  const smsMessage = buildTransactionSms(event.amount, event.type, event.status, event.transactionId);
  await sendSms(mockPhone, smsMessage);

  // Push (mock userId = accountId)
  const { title, body: pushBody } = buildTransactionPush(event.amount, event.type, event.status);
  await sendPushNotification(accountId, title, pushBody);
}

async function handleNotificationEvent(raw: KafkaMessage): Promise<void> {
  if (!raw.value) return;

  const event: NotificationEvent = JSON.parse(raw.value.toString()) as NotificationEvent;

  console.log(
    `[KAFKA] notification-events | type=${event.type} recipient=${event.recipient}`,
  );

  switch (event.type.toUpperCase()) {
    case 'EMAIL':
      try {
        await sendEmail(event.recipient, event.subject ?? '(no subject)', event.body);
      } catch (err) {
        console.error('[EMAIL] Failed to send notification email:', err);
      }
      break;

    case 'SMS':
      await sendSms(event.recipient, event.body);
      break;

    case 'PUSH':
      await sendPushNotification(event.recipient, event.subject ?? 'eBank', event.body);
      break;

    default:
      console.warn(`[KAFKA] Unknown notification type: ${event.type}`);
  }
}

// ---------------------------------------------------------------------------
// Router
// ---------------------------------------------------------------------------

async function processMessage({ topic, message }: EachMessagePayload): Promise<void> {
  try {
    switch (topic) {
      case TOPICS.TRANSACTION_EVENTS:
        await handleTransactionEvent(message);
        break;
      case TOPICS.NOTIFICATION_EVENTS:
        await handleNotificationEvent(message);
        break;
      default:
        console.warn(`[KAFKA] Received message on unhandled topic: ${topic}`);
    }
    kafkaMessagesConsumedTotal.inc({ topic, status: 'success' });
  } catch (err) {
    kafkaMessagesConsumedTotal.inc({ topic, status: 'failure' });
    console.error(`[KAFKA] Error processing message on topic ${topic}:`, err);
    // Do NOT rethrow — we don't want one bad message to crash the consumer.
  }
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

export async function startKafkaConsumer(): Promise<void> {
  consumer = kafka.consumer({ groupId: config.kafka.groupId });

  await consumer.connect();
  console.log('[KAFKA] Consumer connected.');

  await consumer.subscribe({
    topics: [
      TOPICS.TRANSACTION_EVENTS,
      TOPICS.NOTIFICATION_EVENTS,
    ],
    fromBeginning: false,
  });

  await consumer.run({ eachMessage: processMessage });

  console.log(
    `[KAFKA] Listening on topics: ${Object.values(TOPICS).join(', ')}`,
  );
}

export async function stopKafkaConsumer(): Promise<void> {
  if (consumer) {
    await consumer.disconnect();
    console.log('[KAFKA] Consumer disconnected.');
    consumer = null;
  }
}
