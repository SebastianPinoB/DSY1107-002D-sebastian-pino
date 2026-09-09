import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
export const TOKEN_STORAGE_KEY = 'javiiland_token'

export const axiosClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Inyecta el JWT emitido por /api/usuarios/login o /registro en cada request.
axiosClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Si el token expiró o es inválido, el backend responde 401: limpiamos la
// sesión local para que la UI vuelva a pedir login (evita quedar "logueado"
// en la UI con un token muerto).
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      localStorage.removeItem(TOKEN_STORAGE_KEY)
    }
    return Promise.reject(error)
  }
)

/**
 * Traduce errores de axios/Spring a un mensaje legible para el usuario.
 * Cubre tanto el formato clásico de Spring Boot (errors[].defaultMessage)
 * como un mensaje simple en el cuerpo.
 */
export function extractErrorMessage(error, fallback = 'Ocurrió un error inesperado. Intenta de nuevo.') {
  if (!error?.response) {
    return 'No se pudo contactar al servidor. Verifica que el backend esté corriendo.'
  }

  const { data } = error.response

  if (!data) return fallback

  if (typeof data === 'string') return data

  if (Array.isArray(data.errors) && data.errors.length > 0) {
    return data.errors
      .map((e) => e.defaultMessage || e.message || JSON.stringify(e))
      .join(' ')
  }

  if (data.message) return data.message
  if (data.error) return data.error

  return fallback
}
