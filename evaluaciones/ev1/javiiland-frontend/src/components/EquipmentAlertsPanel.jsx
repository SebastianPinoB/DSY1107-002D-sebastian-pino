import { useEffect, useMemo, useState } from 'react'
import { mantenimientoService } from '../api/mantenimientoService'
import { extractErrorMessage } from '../api/axiosClient'

const REFRESH_INTERVAL = 15000

function formatDateTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return new Intl.DateTimeFormat('es-CL', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export default function EquipmentAlertsPanel({
  refreshKey = 0,
  onEquiposLoaded,
  onActionResult,
  onDataChanged,
}) {
  const [equipos, setEquipos] = useState([])
  const [alertas, setAlertas] = useState([])
  const [hasLoaded, setHasLoaded] = useState(false)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [equipoFiltro, setEquipoFiltro] = useState('ALL')
  const [estadoFiltro, setEstadoFiltro] = useState('ALL')
  const [refreshRequested, setRefreshRequested] = useState(0)
  const [nuevoEquipo, setNuevoEquipo] = useState({ nombre: '', limiteUsos: '', limiteHoras: '' })
  const [savingEquipo, setSavingEquipo] = useState(false)
  const [equipoEnAccion, setEquipoEnAccion] = useState(null)
  const [actionFeedback, setActionFeedback] = useState(null)

  useEffect(() => {
    let active = true
    let timeoutId

    async function refresh(initialLoad = false) {
      if (initialLoad) setLoading(true)
      else setRefreshing(true)

      try {
        const [equiposData, alertasActivas, alertasResueltas] = await Promise.all([
          mantenimientoService.listarEquipos(),
          mantenimientoService.listarAlertas(false),
          mantenimientoService.listarAlertas(true),
        ])

        if (!active) return
        setEquipos(equiposData)
        onEquiposLoaded?.(equiposData)
        setAlertas(
          [...alertasActivas, ...alertasResueltas].sort(
            (first, second) => new Date(second.creadaEn) - new Date(first.creadaEn)
          )
        )
        setHasLoaded(true)
        setError('')
      } catch (requestError) {
        if (active) {
          setError(extractErrorMessage(requestError, 'No se pudieron cargar los equipos y sus alertas.'))
        }
      } finally {
        if (active) {
          setLoading(false)
          setRefreshing(false)
          timeoutId = window.setTimeout(() => refresh(), REFRESH_INTERVAL)
        }
      }
    }

    refresh(true)
    return () => {
      active = false
      window.clearTimeout(timeoutId)
    }
  }, [refreshRequested, refreshKey, onEquiposLoaded])

  function reportAction(result) {
    setActionFeedback(result)
    onActionResult?.(result)
  }

  function reportActionError(action, requestError) {
    reportAction({
      action,
      status: requestError.response?.status ?? 'Error',
      success: false,
      message: extractErrorMessage(requestError, 'No se pudo completar la operación.'),
    })
  }

  async function handleCrearEquipo(event) {
    event.preventDefault()
    setSavingEquipo(true)
    try {
      const request = {
        nombre: nuevoEquipo.nombre.trim(),
        limiteUsos: nuevoEquipo.limiteUsos ? Number(nuevoEquipo.limiteUsos) : null,
        limiteHoras: nuevoEquipo.limiteHoras ? Number(nuevoEquipo.limiteHoras) : null,
      }
      const response = await mantenimientoService.crearEquipo(request)
      reportAction({
        action: 'Crear equipo',
        status: response.status,
        success: true,
        message: `Equipo "${response.data.nombre}" creado.`,
      })
      setNuevoEquipo({ nombre: '', limiteUsos: '', limiteHoras: '' })
      onDataChanged?.()
    } catch (requestError) {
      reportActionError('Crear equipo', requestError)
    } finally {
      setSavingEquipo(false)
    }
  }

  async function handleReportarFalla(event, equipo) {
    event.preventDefault()
    const form = event.currentTarget
    const formData = new FormData(form)
    const detalle = String(formData.get('detalle') || '').trim()
    if (!detalle) return

    setEquipoEnAccion(equipo.id)
    try {
      const response = await mantenimientoService.reportarFalla(equipo.id, detalle)
      reportAction({
        action: `Reportar falla: ${equipo.nombre}`,
        status: response.status,
        success: true,
        message: `Falla enviada. El bloqueo del equipo se procesa de forma asíncrona.`,
      })
      form.reset()
      onDataChanged?.()
    } catch (requestError) {
      reportActionError(`Reportar falla: ${equipo.nombre}`, requestError)
    } finally {
      setEquipoEnAccion(null)
    }
  }

  async function handleCerrarMantencion(equipo) {
    setEquipoEnAccion(equipo.id)
    try {
      const response = await mantenimientoService.cerrarMantencion(equipo.id)
      reportAction({
        action: `Cerrar mantención: ${equipo.nombre}`,
        status: response.status,
        success: true,
        message: `Equipo liberado; contadores reiniciados y alertas resueltas.`,
      })
      onDataChanged?.()
    } catch (requestError) {
      reportActionError(`Cerrar mantención: ${equipo.nombre}`, requestError)
    } finally {
      setEquipoEnAccion(null)
    }
  }

  const alertasFiltradas = useMemo(
    () =>
      alertas.filter((alerta) => {
        const coincideEquipo = equipoFiltro === 'ALL' || String(alerta.equipoId) === equipoFiltro
        const coincideEstado =
          estadoFiltro === 'ALL' ||
          (estadoFiltro === 'ACTIVE' && !alerta.resuelta) ||
          (estadoFiltro === 'RESOLVED' && alerta.resuelta)
        return coincideEquipo && coincideEstado
      }),
    [alertas, equipoFiltro, estadoFiltro]
  )

  function contarAlertasActivas(equipoId) {
    return alertas.filter((alerta) => alerta.equipoId === equipoId && !alerta.resuelta).length
  }

  return (
    <section className="maintenance-panel" aria-labelledby="maintenance-panel-title">
      <div className="maintenance-panel__heading">
        <div>
          <h2 id="maintenance-panel-title">Equipos y alertas de mantención</h2>
          <p>Estado del equipamiento y alertas procesadas desde Kafka.</p>
        </div>
        <button
          className="btn btn--ghost"
          type="button"
          onClick={() => setRefreshRequested((current) => current + 1)}
          disabled={loading || refreshing}
        >
          {refreshing ? 'Actualizando…' : 'Actualizar'}
        </button>
      </div>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {actionFeedback && (
        <p className={actionFeedback.success ? 'admin-action-feedback' : 'form-error'} role="status">
          HTTP {actionFeedback.status} · {actionFeedback.message}
        </p>
      )}
      {loading && <p className="muted" role="status">Cargando equipos y alertas…</p>}

      {!loading && hasLoaded && (
        <>
          <section className="maintenance-panel__section" aria-labelledby="equipment-list-title">
            <div className="maintenance-panel__subheading">
              <div>
                <h3 id="equipment-list-title">Equipos</h3>
                <p className="muted">Crea equipos y simula fallas o el cierre de mantención.</p>
              </div>
            </div>
            <form className="maintenance-team-form" onSubmit={handleCrearEquipo}>
              <label className="maintenance-filter">
                <span>Nombre</span>
                <input
                  required
                  value={nuevoEquipo.nombre}
                  onChange={(event) => setNuevoEquipo((current) => ({ ...current, nombre: event.target.value }))}
                />
              </label>
              <label className="maintenance-filter">
                <span>Límite de usos</span>
                <input
                  type="number"
                  min="1"
                  step="1"
                  value={nuevoEquipo.limiteUsos}
                  onChange={(event) => setNuevoEquipo((current) => ({ ...current, limiteUsos: event.target.value }))}
                />
              </label>
              <label className="maintenance-filter">
                <span>Límite de horas</span>
                <input
                  type="number"
                  min="0.1"
                  step="0.1"
                  value={nuevoEquipo.limiteHoras}
                  onChange={(event) => setNuevoEquipo((current) => ({ ...current, limiteHoras: event.target.value }))}
                />
              </label>
              <div className="maintenance-team-form__actions">
                <button
                  className="btn btn--ghost btn--small"
                  type="button"
                  onClick={() => setNuevoEquipo({ nombre: 'Inflable Castillo', limiteUsos: '3', limiteHoras: '' })}
                >
                  Inflable Castillo · 3 usos
                </button>
                <button
                  className="btn btn--ghost btn--small"
                  type="button"
                  onClick={() => setNuevoEquipo({ nombre: 'Taca-Taca #2', limiteUsos: '', limiteHoras: '10' })}
                >
                  Taca-Taca #2 · 10 horas
                </button>
                <button className="btn btn--primary btn--small" type="submit" disabled={savingEquipo}>
                  {savingEquipo ? 'Creando…' : 'Crear equipo'}
                </button>
              </div>
            </form>

            {equipos.length === 0 ? (
              <p className="empty-state">No hay equipos registrados.</p>
            ) : (
              <div className="maintenance-table-wrap">
                <table className="maintenance-table">
                  <thead>
                    <tr>
                      <th scope="col">Equipo</th>
                      <th scope="col">Estado</th>
                      <th scope="col">Usos</th>
                      <th scope="col">Horas</th>
                      <th scope="col">Alertas activas</th>
                      <th scope="col">Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {equipos.map((equipo) => {
                      const alertasActivas = contarAlertasActivas(equipo.id)
                      return (
                        <tr key={equipo.id}>
                          <th scope="row">
                            <span className="maintenance-table__name">{equipo.nombre}</span>
                            {equipo.mantencionDesde && (
                              <span className="maintenance-table__detail">
                                Mantención desde {equipo.mantencionDesde}
                              </span>
                            )}
                          </th>
                          <td>
                            <span
                              className={`maintenance-badge ${
                                equipo.estado === 'EN_MANTENCION'
                                  ? 'maintenance-badge--maintenance'
                                  : 'maintenance-badge--available'
                              }`}
                            >
                              {equipo.estado}
                            </span>
                          </td>
                          <td>
                            {equipo.usosAcumulados}
                            {equipo.limiteUsos != null && ` / ${equipo.limiteUsos}`}
                          </td>
                          <td>
                            {equipo.horasAcumuladas}
                            {equipo.limiteHoras != null && ` / ${equipo.limiteHoras}`}
                          </td>
                          <td>
                            <span className="maintenance-alert-count">{alertasActivas}</span>
                          </td>
                          <td>
                            {equipo.estado === 'EN_MANTENCION' ? (
                              <button
                                className="btn btn--primary btn--small"
                                type="button"
                                onClick={() => handleCerrarMantencion(equipo)}
                                disabled={equipoEnAccion === equipo.id}
                              >
                                {equipoEnAccion === equipo.id ? 'Cerrando…' : 'Cerrar mantención'}
                              </button>
                            ) : (
                              <form
                                className="maintenance-failure-form"
                                onSubmit={(event) => handleReportarFalla(event, equipo)}
                              >
                                <input name="detalle" aria-label={`Detalle de falla de ${equipo.nombre}`} required />
                                <button
                                  className="btn btn--danger btn--small"
                                  type="submit"
                                  disabled={equipoEnAccion === equipo.id}
                                >
                                  {equipoEnAccion === equipo.id ? 'Enviando…' : 'Reportar falla'}
                                </button>
                              </form>
                            )}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="maintenance-panel__section" aria-labelledby="maintenance-alerts-title">
            <div className="maintenance-panel__subheading">
              <div>
                <h3 id="maintenance-alerts-title">Feed de alertas</h3>
                <p className="muted">Incluye alertas activas y resueltas registradas por el consumidor.</p>
              </div>
              <div className="maintenance-filters">
                <label className="maintenance-filter">
                  <span>Equipo</span>
                  <select value={equipoFiltro} onChange={(event) => setEquipoFiltro(event.target.value)}>
                    <option value="ALL">Todos</option>
                    {equipos.map((equipo) => (
                      <option key={equipo.id} value={equipo.id}>
                        {equipo.nombre}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="maintenance-filter">
                  <span>Estado de alerta</span>
                  <select value={estadoFiltro} onChange={(event) => setEstadoFiltro(event.target.value)}>
                    <option value="ALL">Todas</option>
                    <option value="ACTIVE">Activas</option>
                    <option value="RESOLVED">Resueltas</option>
                  </select>
                </label>
              </div>
            </div>

            {alertasFiltradas.length === 0 ? (
              <p className="empty-state">
                {alertas.length === 0 ? 'Todavía no hay alertas de mantención.' : 'No hay alertas para estos filtros.'}
              </p>
            ) : (
              <ul className="maintenance-alert-list">
                {alertasFiltradas.map((alerta) => (
                  <li className="maintenance-alert" key={alerta.id}>
                    <div className="maintenance-alert__header">
                      <div>
                        <h4>{alerta.equipo}</h4>
                        <p>{alerta.detalle || 'Sin detalle adicional.'}</p>
                      </div>
                      <div className="maintenance-alert__badges">
                        <span className="maintenance-badge maintenance-badge--reason">{alerta.motivo}</span>
                        <span
                          className={`maintenance-badge ${
                            alerta.resuelta
                              ? 'maintenance-badge--resolved'
                              : 'maintenance-badge--active'
                          }`}
                        >
                          {alerta.resuelta ? 'Resuelta' : 'Activa'}
                        </span>
                      </div>
                    </div>
                    <div className="maintenance-alert__meta">
                      <span>Creada: {formatDateTime(alerta.creadaEn)}</span>
                      {alerta.resueltaEn && <span>Resuelta: {formatDateTime(alerta.resueltaEn)}</span>}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </section>
  )
}
