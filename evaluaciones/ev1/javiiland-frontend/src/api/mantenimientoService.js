import { axiosClient } from './axiosClient'

export const mantenimientoService = {
  listarEquipos: () => axiosClient.get('/api/equipos').then((response) => response.data),
  listarAlertas: (resueltas) =>
    axiosClient.get('/api/mantenimiento/alertas', { params: { resueltas } }).then((response) => response.data),
  crearEquipo: (request) => axiosClient.post('/api/equipos', request),
  reportarFalla: (id, detalle) => axiosClient.post(`/api/equipos/${id}/falla`, { detalle }),
  cerrarMantencion: (id) => axiosClient.post(`/api/equipos/${id}/mantencion/cerrar`),
}
