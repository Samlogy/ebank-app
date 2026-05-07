/**
 * Push notification service — mock implementation.
 * In production, replace the body of sendPushNotification with a real FCM call.
 */

export async function sendPushNotification(
  userId: string,
  title: string,
  body: string,
): Promise<void> {
  console.log(`[PUSH MOCK] UserId: ${userId} | Title: ${title} | Body: ${body}`);

  // In production: integrate Firebase Cloud Messaging (FCM)
  //
  // import * as admin from 'firebase-admin';
  // await admin.messaging().send({
  //   notification: { title, body },
  //   token: await getDeviceTokenForUser(userId),  // look up stored FCM token
  // });
}

export function buildTransactionPush(
  amount: number,
  type: string,
  status: string,
): { title: string; body: string } {
  const formattedAmount = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount);

  const typeLabels: Record<string, string> = {
    DEPOSIT: 'Deposit received',
    WITHDRAWAL: 'Withdrawal processed',
    TRANSFER: 'Transfer processed',
    PAYMENT: 'Payment processed',
  };

  const title = typeLabels[type] ?? 'Transaction update';
  const body = `${formattedAmount} — ${status.charAt(0).toUpperCase()}${status.slice(1).toLowerCase()}`;

  return { title, body };
}

