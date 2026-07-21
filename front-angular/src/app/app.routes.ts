import { Routes } from '@angular/router'
import { authGuard } from './core/guards/auth.guard'
import { LayoutComponent } from './layout/layout.component'
import { LoginComponent } from './pages/login/login.component'
import { DashboardComponent } from './pages/dashboard/dashboard.component'
import { AccountsComponent } from './pages/accounts/accounts.component'
import { TransactionsComponent } from './pages/transactions/transactions.component'

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'accounts', component: AccountsComponent },
      { path: 'transactions', component: TransactionsComponent },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
]
