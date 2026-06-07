import client from './client'
import type { AuthResponse, User } from '../types'

export interface RegisterData {
  username: string
  email: string
  password: string
}

export interface LoginData {
  email: string
  password: string
}

export async function register(data: RegisterData): Promise<AuthResponse> {
  const response = await client.post<AuthResponse>('/auth/register', data)
  return response.data
}

export async function login(data: LoginData): Promise<AuthResponse> {
  const response = await client.post<AuthResponse>('/auth/login', data)
  return response.data
}

export async function logout(): Promise<void> {
  await client.post('/auth/logout')
}

export async function getMe(): Promise<User> {
  const response = await client.get<User>('/auth/me')
  return response.data
}
