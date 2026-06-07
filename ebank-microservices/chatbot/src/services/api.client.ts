import axios from 'axios';
import { config } from '../config';

// Each function makes an actual HTTP call to the corresponding microservice.
// On failure, a safe fallback value is returned so the chatbot can still respond gracefully.

export async function getAccountBalance(
  accountId: string,
): Promise<{ accountId: string; balance: number; currency: string }> {
  try {
    const response = await axios.get(
      `${config.services.accounts}/api/accounts/${accountId}`,
    );
    return {
      accountId,
      balance: response.data.balance,
      currency: 'EUR',
    };
  } catch {
    return { accountId, balance: 0, currency: 'EUR' };
  }
}

export async function getRecentTransactions(
  accountId: string,
  limit = 5,
): Promise<any[]> {
  try {
    const response = await axios.get(
      `${config.services.transactions}/api/transactions/account/${accountId}?limit=${limit}`,
    );
    return response.data.slice(0, limit);
  } catch {
    return [];
  }
}

