import { Injectable } from '@angular/core'
import { HttpClient } from '@angular/common/http'
import { Observable } from 'rxjs'
import type { AccountPayload, AccountResponse } from '../models/types'

@Injectable({ providedIn: 'root' })
export class AccountsService {
  constructor(private readonly http: HttpClient) {}

  getAccounts(): Observable<AccountResponse[]> {
    return this.http.get<AccountResponse[]>('/api/accounts')
  }

  getAccount(id: number | string): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`/api/accounts/${id}`)
  }

  createAccount(data: AccountPayload): Observable<AccountResponse> {
    return this.http.post<AccountResponse>('/api/accounts', data)
  }

  updateAccount(id: number | string, data: AccountPayload): Observable<AccountResponse> {
    return this.http.put<AccountResponse>(`/api/accounts/${id}`, data)
  }

  deleteAccount(id: number | string): Observable<void> {
    return this.http.delete<void>(`/api/accounts/${id}`)
  }
}
