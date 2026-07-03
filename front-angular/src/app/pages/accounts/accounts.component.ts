import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component, OnInit } from '@angular/core'
import { AccountsService } from '../../core/services/accounts.service'
import type { AccountPayload, AccountResponse } from '../../core/models/types'
import { AccountModalComponent } from './account-modal/account-modal.component'

const emptyForm: AccountPayload = {
  accountNumber: '',
  accountHolderName: '',
  email: '',
  phoneNumber: '',
  accountType: 'SAVINGS',
  balance: 0,
  address: '',
  status: 'ACTIVE',
}

@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [CommonModule, AccountModalComponent],
  templateUrl: './accounts.component.html',
})
export class AccountsComponent implements OnInit {
  accounts: AccountResponse[] = []
  isLoading = true

  showModal = false
  editTarget: AccountResponse | null = null
  modalError: string | null = null
  modalLoading = false
  deleteConfirm: number | null = null
  deleteLoading = false

  readonly emptyForm = emptyForm

  constructor(private readonly accountsService: AccountsService) {}

  ngOnInit(): void {
    this.fetchAccounts()
  }

  private fetchAccounts(): void {
    this.isLoading = true
    this.accountsService.getAccounts().subscribe((accounts) => {
      this.accounts = accounts
      this.isLoading = false
    })
  }

  get editInitial(): AccountPayload | null {
    if (!this.editTarget) return null
    const {
      accountNumber,
      accountHolderName,
      email,
      phoneNumber,
      accountType,
      balance,
      address,
      status,
    } = this.editTarget
    return { accountNumber, accountHolderName, email, phoneNumber, accountType, balance, address, status }
  }

  openCreateModal(): void {
    this.showModal = true
    this.modalError = null
  }

  openEditModal(account: AccountResponse): void {
    this.editTarget = account
    this.modalError = null
  }

  closeModals(): void {
    this.showModal = false
    this.editTarget = null
  }

  createAccount(data: AccountPayload): void {
    this.modalLoading = true
    this.accountsService.createAccount(data).subscribe({
      next: () => {
        this.fetchAccounts()
        this.showModal = false
        this.modalError = null
        this.modalLoading = false
      },
      error: (err: HttpErrorResponse) => {
        this.modalError = (err.error as { message?: string })?.message ?? 'Failed to create account.'
        this.modalLoading = false
      },
    })
  }

  updateAccount(data: AccountPayload): void {
    if (!this.editTarget) return
    this.modalLoading = true
    this.accountsService.updateAccount(this.editTarget.id, data).subscribe({
      next: () => {
        this.fetchAccounts()
        this.editTarget = null
        this.modalError = null
        this.modalLoading = false
      },
      error: (err: HttpErrorResponse) => {
        this.modalError = (err.error as { message?: string })?.message ?? 'Failed to update account.'
        this.modalLoading = false
      },
    })
  }

  confirmDelete(id: number): void {
    this.deleteConfirm = id
  }

  cancelDelete(): void {
    this.deleteConfirm = null
  }

  performDelete(): void {
    if (this.deleteConfirm === null) return
    this.deleteLoading = true
    this.accountsService.deleteAccount(this.deleteConfirm).subscribe(() => {
      this.fetchAccounts()
      this.deleteConfirm = null
      this.deleteLoading = false
    })
  }

  statusColor(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'bg-green-100 text-green-700'
      case 'INACTIVE':
        return 'bg-yellow-100 text-yellow-700'
      case 'CLOSED':
        return 'bg-red-100 text-red-700'
      default:
        return 'bg-slate-100 text-slate-600'
    }
  }
}
