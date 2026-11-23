import { axiosClient } from './axiosClient'

const TOPICS_URL = '/api/admin/kafka/topics'

export const kafkaAdminService = {
  listarTopicos: () => axiosClient.get(TOPICS_URL).then((response) => response.data),
  obtenerTopico: (nombre) => axiosClient.get(`${TOPICS_URL}/${encodeURIComponent(nombre)}`).then((response) => response.data),
  crearTopico: (request) => axiosClient.post(TOPICS_URL, request),
  actualizarTopico: (nombre, request) =>
    axiosClient.patch(`${TOPICS_URL}/${encodeURIComponent(nombre)}`, request),
  eliminarTopico: (nombre) => axiosClient.delete(`${TOPICS_URL}/${encodeURIComponent(nombre)}`),
  enviarEvento: (request) => axiosClient.post('/api/admin/kafka/test-event', request),
}
