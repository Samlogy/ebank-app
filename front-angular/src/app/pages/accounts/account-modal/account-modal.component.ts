import { CommonModule } from '@angular/common'
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core'
import { FormsModule } from '@angular/forms'
import type { AccountPayload } from '../../../core/models/types'

@Component({
  selector: 'app-account-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './account-modal.component.html',
})
export class AccountModalComponent implements OnInit {
  @Input() title = ''
  @Input() initial!: AccountPayload
  @Input() loading = false
  @Input() error: string | null = null

  @Output() save = new EventEmitter<AccountPayload>()
  @Output() close = new EventEmitter<void>()

  readonly accountTypes = ['SAVINGS', 'CHECKING', 'BUSINESS']
  readonly accountStatuses = ['ACTIVE', 'INACTIVE', 'CLOSED']

  form!: AccountPayload

  ngOnInit(): void {
    this.form = { ...this.initial }
  }

  handleSubmit(): void {
    this.save.emit(this.form)
  }
}
