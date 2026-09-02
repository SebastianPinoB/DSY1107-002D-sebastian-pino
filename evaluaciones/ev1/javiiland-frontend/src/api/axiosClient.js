import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export const axiosClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * El backend aún no implementa Spring Security / JWT (ver README).
 * Cuando exista un token, se puede inyectar aquí, por ejemplo:
 *
 * axiosClient.interceptors.request.use((config) => {
 *   const token = localStorage.getItem('javiiland_token')
 *   if (token) config.headers.Authorization = `Bearer ${token}`
 *   return config
 * })
 */

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
