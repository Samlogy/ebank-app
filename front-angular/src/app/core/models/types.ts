export interface AccountResponse {
  id: number
  accountNumber: string
  accountHolderName: string
  email: string
  phoneNumber: string
  accountType: string
  balance: number
  address: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface TransactionResponse {
  id: string
  fromAccountId: string
  toAccountId: string
  amount: number
  type: string
  status: string
  description: string
  referenceNumber: string
  createdAt: string
  updatedAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  username?: string
  email?: string
}

export interface User {
  id: number | string
  username: string
  email: string
  role: string
}

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

export interface TransactionPayload {
  fromAccountId: string
  toAccountId: string
  amount: number
  type: string
  description: string
}
