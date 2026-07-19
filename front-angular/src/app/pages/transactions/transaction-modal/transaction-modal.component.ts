
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core'
import { FormsModule } from '@angular/forms'
import type { TransactionPayload } from '../../../core/models/types'

const emptyForm: TransactionPayload = {
  fromAccountId: '',
  toAccountId: '',
  amount: 0,
  type: 'TRANSFER',
  description: '',
}

@Component({
    selector: 'app-transaction-modal',
    imports: [FormsModule],
    templateUrl: './transaction-modal.component.html'
})
export class TransactionModalComponent implements OnInit {
  @Input() loading = false
  @Input() error: string | null = null

  @Output() save = new EventEmitter<TransactionPayload>()
  @Output() close = new EventEmitter<void>()

  readonly types = ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'PAYMENT']

  form!: TransactionPayload

  ngOnInit(): void {
    this.form = { ...emptyForm }
  }

  handleSubmit(): void {
    this.save.emit(this.form)
  }
}
