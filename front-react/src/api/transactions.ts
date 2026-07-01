import client from './client'
import type { TransactionResponse } from '../types'

export interface TransactionPayload {
  fromAccountId: string
  toAccountId: string
  amount: number
  type: string
  description: string
}

export async function getTransactions(): Promise<TransactionResponse[]> {
  const response = await client.get<TransactionResponse[]>('/transactions')
  return response.data
}

export async function getAccountTransactions(
  accountId: string | number,
): Promise<TransactionResponse[]> {
  const response = await client.get<TransactionResponse[]>(
    `/transactions/account/${accountId}`,
  )
  return response.data
}

export async function createTransaction(
  data: TransactionPayload,
): Promise<TransactionResponse> {
  const response = await client.post<TransactionResponse>('/transactions', data)
  return response.data
}
