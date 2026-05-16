import twilio from 'twilio';
import { config } from '../config';

const twilioEnabled =
  config.twilio.accountSid &&
  config.twilio.authToken &&
  config.twilio.fromNumber;

const twilioClient = twilioEnabled
  ? twilio(config.twilio.accountSid, config.twilio.authToken)
  : null;

export async function sendSms(to: string, message: string): Promise<void> {
  if (!twilioClient) {
    console.log(`[SMS MOCK] To: ${to} | Message: ${message}`);
    return;
  }

  try {
    await twilioClient.messages.create({
      body: message,
      from: config.twilio.fromNumber,
      to,
    });
    console.log(`[SMS] Sent to ${to}`);
  } catch (err) {
    console.error(`[SMS] Failed to send to ${to}:`, err);
  }
}

export function buildTransactionSms(
  amount: number,
  type: string,
  status: string,
  transactionId: string,
): string {
  const formattedAmount = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount);

  return (
    `[eBank] Your ${type.toLowerCase()} of ${formattedAmount} is ${status.toLowerCase()}. ` +
    `Ref: ${transactionId.slice(0, 8).toUpperCase()}. ` +
    `Not you? Call us immediately.`
  );
}

