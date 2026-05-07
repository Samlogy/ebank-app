import client from './client'
import type { AccountResponse } from '../types'

export interface AccountPayload {
  accountNumber: string
  accountHolderName: string
  email: string
  phoneNumber: string
  accountType: string
  balance: number
  address: string
  status: string
}

export async function getAccounts(): Promise<AccountResponse[]> {
  const response = await client.get<AccountResponse[]>('/accounts')
  return response.data
}

export async function getAccount(id: number | string): Promise<AccountResponse> {
  const response = await client.get<AccountResponse>(`/accounts/${id}`)
  return response.data
}

export async function createAccount(data: AccountPayload): Promise<AccountResponse> {
  const response = await client.post<AccountResponse>('/accounts', data)
  return response.data
}

export async function updateAccount(
  id: number | string,
  data: AccountPayload,
): Promise<AccountResponse> {
  const response = await client.put<AccountResponse>(`/accounts/${id}`, data)
  return response.data
}

export async function deleteAccount(id: number | string): Promise<void> {
  await client.delete(`/accounts/${id}`)
}
