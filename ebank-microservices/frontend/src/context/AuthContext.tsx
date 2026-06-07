import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
} from 'react'
import type { User } from '../types'
import * as authApi from '../api/auth'

interface AuthContextValue {
  token: string | null
  user: User | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (username: string, email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(
    () => localStorage.getItem('ebank_token'),
  )
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState<boolean>(true)

  useEffect(() => {
    if (!token) {
      setLoading(false)
      return
    }
    authApi
      .getMe()
      .then((u) => setUser(u))
      .catch(() => {
        localStorage.removeItem('ebank_token')
        setToken(null)
      })
      .finally(() => setLoading(false))
  }, [token])

  const login = useCallback(async (email: string, password: string) => {
    const data = await authApi.login({ email, password })
    localStorage.setItem('ebank_token', data.accessToken)
    setToken(data.accessToken)
    const me = await authApi.getMe()
    setUser(me)
  }, [])

  const register = useCallback(
    async (username: string, email: string, password: string) => {
      const data = await authApi.register({ username, email, password })
      localStorage.setItem('ebank_token', data.accessToken)
      setToken(data.accessToken)
      const me = await authApi.getMe()
      setUser(me)
    },
    [],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // ignore errors on logout
    } finally {
      localStorage.removeItem('ebank_token')
      setToken(null)
      setUser(null)
    }
  }, [])

  return (
    <AuthContext.Provider value={{ token, user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
