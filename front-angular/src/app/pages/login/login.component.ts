import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component } from '@angular/core'
import { FormsModule } from '@angular/forms'
import { Router } from '@angular/router'
import { AuthService } from '../../core/services/auth.service'

type Tab = 'login' | 'register'

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  tab: Tab = 'login'
  error: string | null = null
  loading = false

  loginEmail = ''
  loginPassword = ''

  regUsername = ''
  regEmail = ''
  regPassword = ''

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  setTab(tab: Tab): void {
    this.tab = tab
    this.error = null
  }

  async handleLogin(): Promise<void> {
    if (!this.loginEmail || !this.loginPassword) {
      this.error = 'Please fill in all fields.'
      return
    }
    this.error = null
    this.loading = true
    try {
      await this.authService.login(this.loginEmail, this.loginPassword)
      this.router.navigate(['/dashboard'])
    } catch (err) {
      this.error = this.extractMessage(err, 'Invalid credentials. Please try again.')
    } finally {
      this.loading = false
    }
  }

  async handleRegister(): Promise<void> {
    if (!this.regUsername || !this.regEmail || !this.regPassword) {
      this.error = 'Please fill in all fields.'
      return
    }
    if (this.regPassword.length < 6) {
      this.error = 'Password must be at least 6 characters.'
      return
    }
    this.error = null
    this.loading = true
    try {
      await this.authService.register(this.regUsername, this.regEmail, this.regPassword)
      this.router.navigate(['/dashboard'])
    } catch (err) {
      this.error = this.extractMessage(err, 'Registration failed. Please try again.')
    } finally {
      this.loading = false
    }
  }

  private extractMessage(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      return (err.error as { message?: string })?.message ?? fallback
    }
    return fallback
  }
}
