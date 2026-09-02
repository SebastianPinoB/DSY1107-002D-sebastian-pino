import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { usuarioService } from '../api/usuarioService'

const STORAGE_KEY = 'javiiland_user'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [usuario, setUsuario] = useState(() => {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  })

  useEffect(() => {
    if (usuario) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(usuario))
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [usuario])

  async function login(loginRequestDto) {
    const usuarioResponse = await usuarioService.login(loginRequestDto)
    setUsuario(usuarioResponse)
    return usuarioResponse
  }

  async function registrar(registroUsuarioDto) {
    const usuarioResponse = await usuarioService.registrar(registroUsuarioDto)
    setUsuario(usuarioResponse)
    return usuarioResponse
  }

  function logout() {
    setUsuario(null)
  }

  const value = useMemo(
    () => ({
      usuario,
      isAuthenticated: Boolean(usuario),
      isAdmin: usuario?.role === 'ADMIN',
      login,
      registrar,
      logout,
    }),
    [usuario]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  return ctx
}
