import { CommonModule } from '@angular/common'
import { Component, OnInit } from '@angular/core'
import { Router } from '@angular/router'
import { forkJoin } from 'rxjs'
import { AuthService } from '../../core/services/auth.service'
import { AccountsService } from '../../core/services/accounts.service'
import { TransactionsService } from '../../core/services/transactions.service'
import type { AccountResponse, TransactionResponse } from '../../core/models/types'

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  accounts: AccountResponse[] = []
  transactions: TransactionResponse[] = []
  isLoading = true

  constructor(
    private readonly authService: AuthService,
    private readonly accountsService: AccountsService,
    private readonly transactionsService: TransactionsService,
    private readonly router: Router,
  ) {}

  get user() {
    return this.authService.user()
  }

  get totalBalance(): number {
    return this.accounts.reduce((sum, a) => sum + (a.balance ?? 0), 0)
  }

  get recentTransactions(): TransactionResponse[] {
    return [...this.transactions]
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 5)
  }

  ngOnInit(): void {
    forkJoin({
      accounts: this.accountsService.getAccounts(),
      transactions: this.transactionsService.getTransactions(),
    }).subscribe(({ accounts, transactions }) => {
      this.accounts = accounts
      this.transactions = transactions
      this.isLoading = false
    })
  }

  goToAccounts(): void {
    this.router.navigate(['/accounts'])
  }

  goToTransactions(): void {
    this.router.navigate(['/transactions'])
  }

  statusColor(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'bg-green-100 text-green-700'
      case 'PENDING':
        return 'bg-yellow-100 text-yellow-700'
      default:
        return 'bg-red-100 text-red-700'
    }
  }
}
