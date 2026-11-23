import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { reservaService } from '../api/reservaService'
import { usuarioService } from '../api/usuarioService'
import { extractErrorMessage } from '../api/axiosClient'
import ReservaCard from '../components/ReservaCard'
import EquipmentAlertsPanel from '../components/EquipmentAlertsPanel'
import KafkaScenarioLab from '../components/KafkaScenarioLab'

const FILTROS = [
  { id: 'ALL', label: 'Todas' },
  { id: 'CONFIRMED', label: 'Confirmadas' },
  { id: 'CANCELLED', label: 'Canceladas' },
]

const CREATE_FORM_INICIAL = { usuarioId: '', reservationDate: '', eventName: '', description: '', esBloqueo: false }

export default function AdminDashboardPage() {
  const [reservas, setReservas] = useState([])
  const [usuarios, setUsuarios] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filtro, setFiltro] = useState('CONFIRMED')

  const [showCreateForm, setShowCreateForm] = useState(false)
  const [createForm, setCreateForm] = useState(CREATE_FORM_INICIAL)
  const [createError, setCreateError] = useState('')
  const [creating, setCreating] = useState(false)

  const [cancellingId, setCancellingId] = useState(null)
  const [deletingId, setDeletingId] = useState(null)
  const [equiposKafka, setEquiposKafka] = useState([])
  const [maintenanceRefreshKey, setMaintenanceRefreshKey] = useState(0)
  const [actionLog, setActionLog] = useState([])

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [reservasData, usuariosData] = await Promise.all([
        reservaService.listar(),
        usuarioService.listar(),
      ])
      setReservas(reservasData)
      setUsuarios(usuariosData)
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudieron cargar las reservas.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleEquiposLoaded = useCallback((equiposData) => {
    setEquiposKafka(equiposData)
  }, [])

  const handleActionResult = useCallback((result) => {
    setActionLog((entries) =>
      [{ ...result, id: `${Date.now()}-${entries.length}`, createdAt: new Date().toISOString() }, ...entries].slice(
        0,
        20
      )
    )
  }, [])

  const refreshScenarioData = useCallback(() => {
    setMaintenanceRefreshKey((current) => current + 1)
    loadData()
  }, [loadData])

  const reservasFiltradas = useMemo(() => {
    const filtradas = filtro === 'ALL' ? reservas : reservas.filter((r) => r.status === filtro)
    return [...filtradas].sort((a, b) => b.fechaReserva.localeCompare(a.fechaReserva))
  }, [reservas, filtro])

  async function handleCancelar(id) {
    if (!window.confirm('¿Seguro que quieres cancelar esta reserva? La fecha quedará libre de nuevo.')) return
    setCancellingId(id)
    try {
      const response = await reservaService.cancelarConRespuesta(id)
      handleActionResult({
        action: `Cancelar reserva #${id}`,
        status: response.status,
        success: true,
        message: 'Reserva cancelada.',
      })
      refreshScenarioData()
    } catch (err) {
      const message = extractErrorMessage(err, 'No se pudo cancelar la reserva.')
      setError(message)
      handleActionResult({
        action: `Cancelar reserva #${id}`,
        status: err.response?.status ?? 'Error',
        success: false,
        message,
      })
    } finally {
      setCancellingId(null)
    }
  }

  async function handleEliminarDefinitivo(id, tipo) {
    const mensaje =
      tipo === 'BLOQUEO'
        ? '¿Eliminar este bloqueo definitivamente? No se puede deshacer.'
        : '¿Eliminar esta reserva definitivamente? Se borra del historial y no se puede deshacer. Si solo quieres liberar la fecha, usa "Cancelar" en vez de esto.'
    if (!window.confirm(mensaje)) return
    setDeletingId(id)
    try {
      const response = await reservaService.eliminarDefinitivo(id)
      handleActionResult({
        action: `Eliminar reserva #${id}`,
        status: response.status,
        success: true,
        message: 'Reserva eliminada definitivamente.',
      })
      refreshScenarioData()
    } catch (err) {
      const message = extractErrorMessage(err, 'No se pudo eliminar la reserva.')
      setError(message)
      handleActionResult({
        action: `Eliminar reserva #${id}`,
        status: err.response?.status ?? 'Error',
        success: false,
        message,
      })
    } finally {
      setDeletingId(null)
    }
  }

  function handleCreateChange(e) {
    const { name, type, value, checked } = e.target
    setCreateForm((f) => ({ ...f, [name]: type === 'checkbox' ? checked : value }))
  }

  async function handleCreateSubmit(e) {
    e.preventDefault()
    setCreateError('')

    if (!createForm.esBloqueo && !createForm.usuarioId) {
      setCreateError('Elige a nombre de qué usuario se hace la reserva, o marca "Bloquear día".')
      return
    }

    setCreating(true)
    try {
      const response = await reservaService.crearComoAdminConRespuesta({
        reservationDate: createForm.reservationDate,
        eventName: createForm.eventName,
        description: createForm.description,
        usuarioId: createForm.esBloqueo ? undefined : Number(createForm.usuarioId),
      })
      handleActionResult({
        action: 'Crear reserva administrativa',
        status: response.status,
        success: true,
        message: `Reserva ${response.data?.id ?? ''} creada.`,
      })
      setCreateForm(CREATE_FORM_INICIAL)
      setShowCreateForm(false)
      refreshScenarioData()
    } catch (err) {
      const message = extractErrorMessage(err, 'No se pudo crear la reserva. Verifica que la fecha esté libre.')
      setCreateError(message)
      handleActionResult({
        action: 'Crear reserva administrativa',
        status: err.response?.status ?? 'Error',
        success: false,
        message,
      })
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="page">
      <div className="page__heading page__heading--split">
        <div>
          <h1>Panel de administración</h1>
          <p>Gestiona todas las reservas de Javiiland: crea, edita, cancela o bloquea cualquier fecha.</p>
        </div>
        <button className="btn btn--primary" onClick={() => setShowCreateForm((v) => !v)}>
          {showCreateForm ? 'Cerrar' : 'Nueva reserva'}
        </button>
      </div>

      <EquipmentAlertsPanel
        refreshKey={maintenanceRefreshKey}
        onEquiposLoaded={handleEquiposLoaded}
        onActionResult={handleActionResult}
        onDataChanged={refreshScenarioData}
      />
      <KafkaScenarioLab
        equipos={equiposKafka}
        reservas={reservas}
        onActionResult={handleActionResult}
        onDataChanged={refreshScenarioData}
      />

      <section className="admin-action-log card" aria-labelledby="admin-action-log-title">
        <div className="kafka-lab__section-heading">
          <div>
            <h2 id="admin-action-log-title">Respuestas del backend</h2>
            <p className="muted">Últimas operaciones HTTP ejecutadas desde los escenarios Kafka.</p>
          </div>
        </div>
        {actionLog.length === 0 ? (
          <p className="empty-state">Las respuestas de las operaciones aparecerán aquí.</p>
        ) : (
          <ol className="admin-action-log__list">
            {actionLog.map((entry) => (
              <li className={entry.success ? 'admin-action-log__entry' : 'admin-action-log__entry admin-action-log__entry--error'} key={entry.id}>
                <span className="admin-action-log__time">
                  {new Date(entry.createdAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                </span>
                <strong>HTTP {entry.status}</strong>
                <span>{entry.action}: {entry.message}</span>
              </li>
            ))}
          </ol>
        )}
      </section>

      {showCreateForm && (
        <form onSubmit={handleCreateSubmit} className="form card admin-create-form">
          <h2>Crear reserva o bloquear un día</h2>

          <label className="field field--checkbox">
            <input type="checkbox" name="esBloqueo" checked={createForm.esBloqueo} onChange={handleCreateChange} />
            <span>Bloquear día (sin cliente asociado) — mantención, uso personal, corrección de error, etc.</span>
          </label>

          <div className="form-row">
            {!createForm.esBloqueo && (
              <label className="field">
                <span>Usuario</span>
                <select name="usuarioId" value={createForm.usuarioId} onChange={handleCreateChange} required>
                  <option value="">Selecciona un usuario</option>
                  {usuarios.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.fullName || u.username} (@{u.username})
                    </option>
                  ))}
                </select>
              </label>
            )}

            <label className="field">
              <span>Fecha</span>
              <input
                type="date"
                name="reservationDate"
                value={createForm.reservationDate}
                onChange={handleCreateChange}
                required
              />
            </label>
          </div>

          <label className="field">
            <span>{createForm.esBloqueo ? 'Motivo del bloqueo' : 'Nombre del evento'}</span>
            <input
              name="eventName"
              value={createForm.eventName}
              onChange={handleCreateChange}
              placeholder={createForm.esBloqueo ? 'Mantención del local' : ''}
              required
            />
          </label>

          <label className="field">
            <span>Descripción (opcional)</span>
            <textarea name="description" value={createForm.description} onChange={handleCreateChange} rows={3} />
          </label>

          {createError && <p className="form-error">{createError}</p>}

          <button className="btn btn--primary" type="submit" disabled={creating}>
            {creating ? 'Guardando…' : createForm.esBloqueo ? 'Bloquear día' : 'Crear reserva'}
          </button>
        </form>
      )}

      <div className="tabs">
        {FILTROS.map((f) => (
          <button
            key={f.id}
            className={`tab ${filtro === f.id ? 'tab--active' : ''}`}
            onClick={() => setFiltro(f.id)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {error && <p className="form-error">{error}</p>}
      {loading && <p className="muted">Cargando…</p>}

      {!loading && reservasFiltradas.length === 0 && !error && (
        <p className="muted">No hay reservas en esta categoría.</p>
      )}

      <div className="reserva-grid">
        {reservasFiltradas.map((reserva) => (
          <ReservaCard
            key={reserva.id}
            reserva={reserva}
            showUsuario
            footer={
              <>
                <Link to={`/admin/reservas/${reserva.id}/editar`} className="btn btn--ghost btn--small">
                  Editar
                </Link>
                {reserva.status !== 'CANCELLED' && (
                  <button
                    className="btn btn--danger btn--small"
                    onClick={() => handleCancelar(reserva.id)}
                    disabled={cancellingId === reserva.id}
                  >
                    {cancellingId === reserva.id ? 'Cancelando…' : 'Cancelar'}
                  </button>
                )}
                <button
                  className="btn btn--danger btn--small"
                  onClick={() => handleEliminarDefinitivo(reserva.id, reserva.tipo)}
                  disabled={deletingId === reserva.id}
                >
                  {deletingId === reserva.id ? 'Eliminando…' : 'Eliminar'}
                </button>
              </>
            }
          />
        ))}
      </div>
    </div>
  )
}
