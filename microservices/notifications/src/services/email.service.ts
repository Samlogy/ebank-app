import nodemailer from 'nodemailer';
import { config } from '../config';
import { TransactionEvent } from '../types/events';
import { notificationSendDurationSeconds, notificationsSentTotal } from '../metrics';

const transporter = nodemailer.createTransport({
  host: config.smtp.host,
  port: config.smtp.port,
  secure: false, // MailHog doesn't use TLS
});

export async function sendEmail(to: string, subject: string, body: string): Promise<void> {
  const stopTimer = notificationSendDurationSeconds.startTimer({ channel: 'email' });
  try {
    await transporter.sendMail({
      from: config.smtp.from,
      to,
      subject,
      html: body,
    });
    notificationsSentTotal.inc({ channel: 'email', status: 'success' });
    console.log(`[EMAIL] Sent to ${to}: ${subject}`);
  } catch (err) {
    notificationsSentTotal.inc({ channel: 'email', status: 'failure' });
    throw err;
  } finally {
    stopTimer();
  }
}

export function buildTransactionEmail(event: TransactionEvent): { subject: string; body: string } {
  const formattedAmount = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(event.amount);

  const formattedDate = new Date(event.occurredAt).toLocaleString('en-US', {
    dateStyle: 'long',
    timeStyle: 'short',
  });

  const typeLabels: Record<string, string> = {
    DEPOSIT: 'Deposit',
    WITHDRAWAL: 'Withdrawal',
    TRANSFER: 'Transfer',
    PAYMENT: 'Payment',
  };

  const typeLabel = typeLabels[event.type] ?? event.type;

  const statusColor: Record<string, string> = {
    COMPLETED: '#22c55e',
    PENDING: '#f59e0b',
    FAILED: '#ef4444',
  };

  const color = statusColor[event.status] ?? '#6b7280';

  const subject = `eBank — ${typeLabel} of ${formattedAmount} ${event.status.toLowerCase()}`;

  const fromRow =
    event.fromAccountId
      ? `<tr>
           <td style="padding:6px 0;color:#6b7280;font-size:14px;">From account</td>
           <td style="padding:6px 0;font-size:14px;font-weight:600;">${event.fromAccountId}</td>
         </tr>`
      : '';

  const toRow =
    event.toAccountId
      ? `<tr>
           <td style="padding:6px 0;color:#6b7280;font-size:14px;">To account</td>
           <td style="padding:6px 0;font-size:14px;font-weight:600;">${event.toAccountId}</td>
         </tr>`
      : '';

  const body = `
<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0"></head>
<body style="margin:0;padding:0;background:#f3f4f6;font-family:Arial,sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:40px 0;">
    <tr>
      <td align="center">
        <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.1);">
          <!-- Header -->
          <tr>
            <td style="background:#1e40af;padding:24px 32px;">
              <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">eBank</h1>
              <p style="margin:4px 0 0;color:#bfdbfe;font-size:13px;">Transaction Notification</p>
            </td>
          </tr>
          <!-- Body -->
          <tr>
            <td style="padding:32px;">
              <p style="margin:0 0 24px;font-size:16px;color:#111827;">
                A transaction has been processed on your account.
              </p>
              <table width="100%" cellpadding="0" cellspacing="0" style="border-top:1px solid #e5e7eb;border-bottom:1px solid #e5e7eb;margin-bottom:24px;">
                <tr>
                  <td style="padding:16px 0;">
                    <table width="100%" cellpadding="0" cellspacing="0">
                      <tr>
                        <td style="padding:6px 0;color:#6b7280;font-size:14px;">Transaction ID</td>
                        <td style="padding:6px 0;font-size:14px;font-family:monospace;">${event.transactionId}</td>
                      </tr>
                      <tr>
                        <td style="padding:6px 0;color:#6b7280;font-size:14px;">Type</td>
                        <td style="padding:6px 0;font-size:14px;font-weight:600;">${typeLabel}</td>
                      </tr>
                      <tr>
                        <td style="padding:6px 0;color:#6b7280;font-size:14px;">Amount</td>
                        <td style="padding:6px 0;font-size:18px;font-weight:700;color:#1e40af;">${formattedAmount}</td>
                      </tr>
                      ${fromRow}
                      ${toRow}
                      <tr>
                        <td style="padding:6px 0;color:#6b7280;font-size:14px;">Status</td>
                        <td style="padding:6px 0;">
                          <span style="display:inline-block;padding:2px 10px;border-radius:9999px;background:${color}20;color:${color};font-size:13px;font-weight:600;">${event.status}</span>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:6px 0;color:#6b7280;font-size:14px;">Date</td>
                        <td style="padding:6px 0;font-size:14px;">${formattedDate}</td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
              <p style="margin:0;font-size:13px;color:#6b7280;">
                If you did not authorise this transaction, please contact eBank support immediately.
              </p>
            </td>
          </tr>
          <!-- Footer -->
          <tr>
            <td style="background:#f9fafb;padding:16px 32px;border-top:1px solid #e5e7eb;">
              <p style="margin:0;font-size:12px;color:#9ca3af;text-align:center;">
                &copy; ${new Date().getFullYear()} eBank. This is an automated message — please do not reply.
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;

  return { subject, body };
}

