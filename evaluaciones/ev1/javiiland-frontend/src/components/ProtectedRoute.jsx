import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function ProtectedRoute({ children, requireAdmin = false }) {
  const { isAuthenticated, isAdmin, cargando } = useAuth()
  const location = useLocation()

  // Esperamos a que termine la validación del token contra /api/usuarios/me
  // antes de decidir; si no, un refresh redirigiría a /ingresar por un instante.
  if (cargando) {
    return null
  }

  if (!isAuthenticated) {
    return <Navigate to="/ingresar" state={{ from: location }} replace />
  }

  if (requireAdmin && !isAdmin) {
    return <Navigate to="/" replace />
  }

  return children
}
