import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext'; // Asegúrate de que esta ruta apunte a tu AuthContext

export default function ProtectedRoute({
  children,
  requireAdmin = false,
  inlineAuthNotice = false,
}) {
  const { isAuthenticated, isAdmin, cargando, login } = useAuth();
  const location = useLocation();

  // 1. Espera a que termine la validación del token
  if (cargando) {
    return null;
  }

  // 2. Si no está autenticado
  if (!isAuthenticated) {
    // Si prefieres mostrar el mensaje con botón en lugar de redirigir
    if (inlineAuthNotice) {
      return (
        <section>
          <p>Debes iniciar sesión para acceder a este contenido.</p>
          <button type="button" onClick={() => login?.()}>
            Iniciar sesión
          </button>
        </section>
      );
    }

    // Por defecto: Redirige a /ingresar guardando la ruta previa
    return <Navigate to="/ingresar" state={{ from: location }} replace />;
  }

  // 3. Si requiere rol de administrador y no lo es
  if (requireAdmin && !isAdmin) {
    return <Navigate to="/" replace />;
  }

  // 4. Si todo está correcto, muestra el contenido
  return children;
}