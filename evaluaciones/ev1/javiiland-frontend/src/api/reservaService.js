import { axiosClient } from './axiosClient'

/**
 * Refleja ReservaController.java (/api/reservas)
 */
export const reservaService = {
  crear: (usuarioId, reservaRequestDto) =>
    axiosClient
      .post('/api/reservas', reservaRequestDto, { params: { usuarioId } })
      .then((res) => res.data),

  listar: () => axiosClient.get('/api/reservas').then((res) => res.data),

  listarPorUsuario: (usuarioId) =>
    axiosClient.get(`/api/reservas/usuario/${usuarioId}`).then((res) => res.data),

  calendario: (inicio, fin) =>
    axiosClient
      .get('/api/reservas/calendario', { params: { inicio, fin } })
      .then((res) => res.data),

  buscarPorId: (id) => axiosClient.get(`/api/reservas/${id}`).then((res) => res.data),

  actualizar: (id, reservaUpdateDto) =>
    axiosClient.put(`/api/reservas/${id}`, reservaUpdateDto).then((res) => res.data),

  cancelar: (id) => axiosClient.delete(`/api/reservas/${id}`).then((res) => res.data),
}
