import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component, OnInit } from '@angular/core'
import { TransactionsService } from '../../core/services/transactions.service'
import type { TransactionPayload, TransactionResponse } from '../../core/models/types'
import { TransactionModalComponent } from './transaction-modal/transaction-modal.component'

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [CommonModule, TransactionModalComponent],
  templateUrl: './transactions.component.html',
})
export class TransactionsComponent implements OnInit {
  transactions: TransactionResponse[] = []
  isLoading = true

  showModal = false
  modalError: string | null = null
  modalLoading = false

  constructor(private readonly transactionsService: TransactionsService) {}

  ngOnInit(): void {
    this.fetchTransactions()
  }

  private fetchTransactions(): void {
    this.isLoading = true
    this.transactionsService.getTransactions().subscribe((transactions) => {
      this.transactions = transactions
      this.isLoading = false
    })
  }

  get sorted(): TransactionResponse[] {
    return [...this.transactions].sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
  }

  openCreateModal(): void {
    this.showModal = true
    this.modalError = null
  }

  closeModal(): void {
    this.showModal = false
  }

  createTransaction(data: TransactionPayload): void {
    this.modalLoading = true
    this.transactionsService.createTransaction(data).subscribe({
      next: () => {
        this.fetchTransactions()
        this.showModal = false
        this.modalError = null
        this.modalLoading = false
      },
      error: (err: HttpErrorResponse) => {
        this.modalError = (err.error as { message?: string })?.message ?? 'Failed to create transaction.'
        this.modalLoading = false
      },
    })
  }

  statusColor(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'bg-green-100 text-green-700'
      case 'PENDING':
        return 'bg-yellow-100 text-yellow-700'
      case 'FAILED':
        return 'bg-red-100 text-red-700'
      default:
        return 'bg-slate-100 text-slate-600'
    }
  }

  typeColor(type: string): string {
    switch (type) {
      case 'DEPOSIT':
        return 'bg-blue-100 text-blue-700'
      case 'WITHDRAWAL':
        return 'bg-orange-100 text-orange-700'
      case 'TRANSFER':
        return 'bg-purple-100 text-purple-700'
      case 'PAYMENT':
        return 'bg-pink-100 text-pink-700'
      default:
        return 'bg-slate-100 text-slate-600'
    }
  }
}
