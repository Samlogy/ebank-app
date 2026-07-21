import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getTransactions, createTransaction } from '../api/transactions'
import type { TransactionPayload } from '../api/transactions'

const TX_TYPES = ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'PAYMENT']

const emptyForm: TransactionPayload = {
  fromAccountId: '',
  toAccountId: '',
  amount: 0,
  type: 'TRANSFER',
  description: '',
}

function NewTransactionModal({
  onSubmit,
  onClose,
  loading,
  error,
}: {
  onSubmit: (data: TransactionPayload) => void
  onClose: () => void
  loading: boolean
  error: string | null
}) {
  const [form, setForm] = useState<TransactionPayload>(emptyForm)

  const set = (field: keyof TransactionPayload, value: string | number) =>
    setForm((f) => ({ ...f, [field]: value }))

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit(form)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden">
        <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
          <h2 className="text-slate-900 font-semibold text-lg">New Transaction</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
              {error}
            </div>
          )}
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Type *</label>
            <select required value={form.type} onChange={(e) => set('type', e.target.value)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500">
              {TX_TYPES.map((t) => <option key={t}>{t}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">From Account ID *</label>
            <input required value={form.fromAccountId}
              onChange={(e) => set('fromAccountId', e.target.value)}
              placeholder="Source account ID"
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">To Account ID *</label>
            <input required value={form.toAccountId}
              onChange={(e) => set('toAccountId', e.target.value)}
              placeholder="Destination account ID"
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Amount *</label>
            <input required type="number" min="0.01" step="0.01" value={form.amount}
              onChange={(e) => set('amount', parseFloat(e.target.value) || 0)}
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Description</label>
            <input value={form.description}
              onChange={(e) => set('description', e.target.value)}
              placeholder="Optional note"
              className="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-lg transition-colors">
              Cancel
            </button>
            <button type="submit" disabled={loading}
              className="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 rounded-lg transition-colors flex items-center gap-2">
              {loading && <span className="animate-spin rounded-full h-3.5 w-3.5 border-b-2 border-white" />}
              Submit
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function TransactionsPage() {
  const qc = useQueryClient()
  const [showModal, setShowModal] = useState(false)
  const [modalError, setModalError] = useState<string | null>(null)

  const { data: transactions = [], isLoading } = useQuery({
    queryKey: ['transactions'],
    queryFn: getTransactions,
  })

  const createMutation = useMutation({
    mutationFn: createTransaction,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['transactions'] })
      setShowModal(false)
      setModalError(null)
    },
    onError: (err: unknown) => {
      setModalError(
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? 'Failed to create transaction.',
      )
    },
  })

  const statusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED': return 'bg-green-100 text-green-700'
      case 'PENDING': return 'bg-yellow-100 text-yellow-700'
      case 'FAILED': return 'bg-red-100 text-red-700'
      default: return 'bg-slate-100 text-slate-600'
    }
  }

  const typeColor = (type: string) => {
    switch (type) {
      case 'DEPOSIT': return 'bg-blue-100 text-blue-700'
      case 'WITHDRAWAL': return 'bg-orange-100 text-orange-700'
      case 'TRANSFER': return 'bg-purple-100 text-purple-700'
      case 'PAYMENT': return 'bg-pink-100 text-pink-700'
      default: return 'bg-slate-100 text-slate-600'
    }
  }

  const sorted = [...transactions].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  )

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Transactions</h1>
          <p className="text-slate-500 mt-1">All financial transactions</p>
        </div>
        <button
          onClick={() => { setShowModal(true); setModalError(null) }}
          className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + New Transaction
        </button>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-20">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
          </div>
        ) : sorted.length === 0 ? (
          <p className="text-slate-500 text-sm text-center py-16">No transactions found.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 text-slate-500 text-left">
                  <th className="px-6 py-3 font-medium">Type</th>
                  <th className="px-6 py-3 font-medium">Amount</th>
                  <th className="px-6 py-3 font-medium">From → To</th>
                  <th className="px-6 py-3 font-medium">Description</th>
                  <th className="px-6 py-3 font-medium">Status</th>
                  <th className="px-6 py-3 font-medium">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sorted.map((tx) => (
                  <tr key={tx.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-6 py-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${typeColor(tx.type)}`}>
                        {tx.type}
                      </span>
                    </td>
                    <td className="px-6 py-3 font-semibold text-slate-900">
                      ${tx.amount.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                    </td>
                    <td className="px-6 py-3 text-slate-600 font-mono text-xs">
                      {tx.fromAccountId} → {tx.toAccountId}
                    </td>
                    <td className="px-6 py-3 text-slate-500 max-w-xs truncate">
                      {tx.description || '—'}
                    </td>
                    <td className="px-6 py-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${statusColor(tx.status)}`}>
                        {tx.status}
                      </span>
                    </td>
                    <td className="px-6 py-3 text-slate-500">
                      {new Date(tx.createdAt).toLocaleDateString()}{' '}
                      <span className="text-slate-400 text-xs">
                        {new Date(tx.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showModal && (
        <NewTransactionModal
          onSubmit={(data) => createMutation.mutate(data)}
          onClose={() => setShowModal(false)}
          loading={createMutation.isPending}
          error={modalError}
        />
      )}
    </div>
  )
}
