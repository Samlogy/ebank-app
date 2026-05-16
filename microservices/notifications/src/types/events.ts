export interface TransactionEvent {
  eventType: string;
  transactionId: string;
  fromAccountId?: string;
  toAccountId?: string;
  amount: number;
  type: string;   // DEPOSIT, WITHDRAWAL, TRANSFER, PAYMENT
  status: string;
  occurredAt: string;
}

export interface NotificationEvent {
  type: string;   // EMAIL, SMS, PUSH
  recipient: string;
  subject?: string;
  body: string;
  occurredAt: string;
}
