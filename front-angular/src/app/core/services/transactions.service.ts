import { Injectable } from '@angular/core'
import { HttpClient } from '@angular/common/http'
import { Observable } from 'rxjs'
import type { TransactionPayload, TransactionResponse } from '../models/types'

@Injectable({ providedIn: 'root' })
export class TransactionsService {
  constructor(private readonly http: HttpClient) {}

  getTransactions(): Observable<TransactionResponse[]> {
    return this.http.get<TransactionResponse[]>('/api/transactions')
  }

  getAccountTransactions(accountId: string | number): Observable<TransactionResponse[]> {
    return this.http.get<TransactionResponse[]>(`/api/transactions/account/${accountId}`)
  }

  createTransaction(data: TransactionPayload): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>('/api/transactions', data)
  }
}
