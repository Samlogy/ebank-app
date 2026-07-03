import { Injectable, computed, signal } from '@angular/core'
import { HttpClient } from '@angular/common/http'
import { firstValueFrom } from 'rxjs'
import type { AuthResponse, User } from '../models/types'

const TOKEN_KEY = 'ebank_token'

export interface LoginData {
  email: string
  password: string
}

export interface RegisterData {
  username: string
  email: string
  password: string
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_KEY))
  private readonly userSignal = signal<User | null>(null)
  private readonly loadingSignal = signal<boolean>(true)

  readonly token = computed(() => this.tokenSignal())
  readonly user = computed(() => this.userSignal())
  readonly loading = computed(() => this.loadingSignal())

  constructor(private readonly http: HttpClient) {
    this.restoreSession()
  }

  private async restoreSession(): Promise<void> {
    const token = this.tokenSignal()
    if (!token) {
      this.loadingSignal.set(false)
      return
    }
    try {
      const me = await firstValueFrom(this.http.get<User>('/api/auth/me'))
      this.userSignal.set(me)
    } catch {
      localStorage.removeItem(TOKEN_KEY)
      this.tokenSignal.set(null)
    } finally {
      this.loadingSignal.set(false)
    }
  }

  async login(email: string, password: string): Promise<void> {
    const data = await firstValueFrom(
      this.http.post<AuthResponse>('/api/auth/login', { email, password } as LoginData),
    )
    localStorage.setItem(TOKEN_KEY, data.accessToken)
    this.tokenSignal.set(data.accessToken)
    const me = await firstValueFrom(this.http.get<User>('/api/auth/me'))
    this.userSignal.set(me)
  }

  async register(username: string, email: string, password: string): Promise<void> {
    const data = await firstValueFrom(
      this.http.post<AuthResponse>('/api/auth/register', {
        username,
        email,
        password,
      } as RegisterData),
    )
    localStorage.setItem(TOKEN_KEY, data.accessToken)
    this.tokenSignal.set(data.accessToken)
    const me = await firstValueFrom(this.http.get<User>('/api/auth/me'))
    this.userSignal.set(me)
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/logout', {}))
    } catch {
      // ignore errors on logout
    } finally {
      localStorage.removeItem(TOKEN_KEY)
      this.tokenSignal.set(null)
      this.userSignal.set(null)
    }
  }
}
