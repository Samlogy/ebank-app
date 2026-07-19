import { CommonModule } from '@angular/common'
import { Component } from '@angular/core'
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router'
import { AuthService } from '../core/services/auth.service'
import { ChatWidgetComponent } from '../components/chat-widget/chat-widget.component'

interface NavItem {
  to: string
  label: string
  icon: 'home' | 'accounts' | 'transactions'
}

@Component({
    selector: 'app-layout',
    imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, ChatWidgetComponent],
    templateUrl: './layout.component.html'
})
export class LayoutComponent {
  readonly navItems: NavItem[] = [
    { to: '/dashboard', label: 'Dashboard', icon: 'home' },
    { to: '/accounts', label: 'Accounts', icon: 'accounts' },
    { to: '/transactions', label: 'Transactions', icon: 'transactions' },
  ]

  constructor(
    readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  get user() {
    return this.authService.user()
  }

  get token() {
    return this.authService.token()
  }

  userInitial(): string {
    return this.user?.username?.charAt(0).toUpperCase() ?? 'U'
  }

  async handleLogout(): Promise<void> {
    await this.authService.logout()
    this.router.navigate(['/login'])
  }
}
