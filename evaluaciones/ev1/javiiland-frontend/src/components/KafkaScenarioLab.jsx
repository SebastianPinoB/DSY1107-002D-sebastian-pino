import { useEffect, useMemo, useState } from 'react'
import { extractErrorMessage } from '../api/axiosClient'
import { kafkaAdminService } from '../api/kafkaAdminService'
import { mantenimientoService } from '../api/mantenimientoService'
import { reservaService } from '../api/reservaService'

const INITIAL_RESERVATION = { reservationDate: '', eventName: '', description: '' }
const INITIAL_TOPIC = { name: '', partitions: '1', replicationFactor: '1', retentionMs: '86400000' }

function successMessage(data, fallback) {
  return data?.message || data?.mensaje || fallback
}

function formatRequestError(error) {
  return {
    status: error.response?.status ?? 'Error',
    message: extractErrorMessage(error, 'No se pudo completar la operación.'),
  }
}

function wait(milliseconds) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds))
}

export default function KafkaScenarioLab({ equipos, reservas, onActionResult, onDataChanged }) {
  const [activeTab, setActiveTab] = useState('reservas')
  const [feedback, setFeedback] = useState(null)
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const [reservation, setReservation] = useState(INITIAL_RESERVATION)
  const [selectedEquipos, setSelectedEquipos] = useState({})
  const [reservaCancelarId, setReservaCancelarId] = useState('')

  const [equipoEventoId, setEquipoEventoId] = useState('')
  const [testingEvent, setTestingEvent] = useState(false)

  const [topics, setTopics] = useState([])
  const [loadingTopics, setLoadingTopics] = useState(false)
  const [topicError, setTopicError] = useState('')
  const [topicsRefreshKey, setTopicsRefreshKey] = useState(0)
  const [newTopic, setNewTopic] = useState(INITIAL_TOPIC)
  const [topicEdits, setTopicEdits] = useState({})
  const [topicAction, setTopicAction] = useState('')

  const reservasActivas = useMemo(
    () =>
      [...reservas]
        .filter((reservaItem) => reservaItem.status === 'CONFIRMED')
        .sort((first, second) => second.fechaReserva.localeCompare(first.fechaReserva))
        .slice(0, 10),
    [reservas]
  )

  useEffect(() => {
    if (activeTab !== 'topics') return undefined
    let active = true
    setLoadingTopics(true)
    setTopicError('')

    async function loadTopics() {
      try {
        const names = await kafkaAdminService.listarTopicos()
        const details = await Promise.all(names.map((name) => kafkaAdminService.obtenerTopico(name)))
        if (active) setTopics(details)
      } catch (error) {
        if (active) setTopicError(extractErrorMessage(error, 'No se pudieron cargar los tópicos de Kafka.'))
      } finally {
        if (active) setLoadingTopics(false)
      }
    }

    loadTopics()
    return () => {
      active = false
    }
  }, [activeTab, topicsRefreshKey])

  function recordResult(action, status, success, message) {
    const result = { action, status, success, message }
    setFeedback(result)
    onActionResult?.(result)
  }

  function recordError(action, error, updateTopicError = false) {
    const result = formatRequestError(error)
    recordResult(action, result.status, false, result.message)
    if (updateTopicError) setTopicError(result.message)
  }

  function notifyDataChanged() {
    onDataChanged?.()
  }

  function refreshAfterKafkaProcessing() {
    notifyDataChanged()
    window.setTimeout(notifyDataChanged, 800)
    window.setTimeout(notifyDataChanged, 1800)
  }

  function toggleEquipo(equipoId) {
    setSelectedEquipos((current) => {
      const next = { ...current }
      if (next[equipoId]) delete next[equipoId]
      else next[equipoId] = '1'
      return next
    })
  }

  async function handleCrearReserva(event) {
    event.preventDefault()
    setFormError('')
    const equipoUso = Object.entries(selectedEquipos).map(([equipoId, horas]) => ({
      equipoId: Number(equipoId),
      horas: Number(horas),
    }))
    if (equipoUso.length === 0) {
      setFormError('Selecciona al menos un equipo para la reserva.')
      return
    }

    setSubmitting(true)
    try {
      const response = await reservaService.crearConRespuesta({
        ...reservation,
        equipos: equipoUso,
      })
      recordResult(
        'Crear reserva con equipos',
        response.status,
        true,
        successMessage(response.data, `Reserva ${response.data?.id ?? ''} creada.`)
      )
      setReservation(INITIAL_RESERVATION)
      setSelectedEquipos({})
      refreshAfterKafkaProcessing()
    } catch (error) {
      recordError('Crear reserva con equipos', error)
      setFormError(formatRequestError(error).message)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleCancelarReserva(event) {
    event.preventDefault()
    if (!reservaCancelarId) return
    const reservaId = Number(reservaCancelarId)
    const selected = reservasActivas.find((item) => item.id === reservaId)
    if (!window.confirm(`¿Cancelar la reserva ${selected?.nombreEvento || `#${reservaId}`}?`)) return

    setSubmitting(true)
    try {
      const response = await reservaService.cancelarConRespuesta(reservaId)
      recordResult(
        `Cancelar reserva #${reservaId}`,
        response.status,
        true,
        successMessage(response.data, 'Reserva cancelada; Kafka procesará la reversa de uso.')
      )
      refreshAfterKafkaProcessing()
    } catch (error) {
      recordError(`Cancelar reserva #${reservaId}`, error)
    } finally {
      setSubmitting(false)
    }
  }

  async function waitForUsageIncrement(equipoId, initialCount) {
    const deadline = Date.now() + 10000
    let currentCount = initialCount

    while (Date.now() < deadline) {
      await wait(500)
      const latestEquipos = await mantenimientoService.listarEquipos()
      const equipo = latestEquipos.find((item) => item.id === Number(equipoId))
      if (!equipo) break
      currentCount = equipo.usosAcumulados
      if (currentCount > initialCount) break
    }

    return currentCount - initialCount
  }

  async function handleTestIdempotency() {
    if (!equipoEventoId) {
      setFormError('Selecciona un equipo para probar idempotencia.')
      return
    }

    setFormError('')
    setTestingEvent(true)
    const action = 'Prueba de idempotencia'
    try {
      const equipoId = Number(equipoEventoId)
      const before = await mantenimientoService.listarEquipos()
      const initialCount = before.find((item) => item.id === equipoId)?.usosAcumulados
      if (initialCount == null) throw new Error('No se encontró el equipo seleccionado.')

      const payload = `{"eventId":"demo-duplicado-1","tipo":"USO","reservaId":null,"equipoId":${equipoId},"horas":1.0,"fecha":"2026-12-20","detalle":null}`
      const request = { key: String(equipoId), payload }
      const [first, second] = await Promise.all([
        kafkaAdminService.enviarEvento(request),
        kafkaAdminService.enviarEvento(request),
      ])
      const increment = await waitForUsageIncrement(equipoId, initialCount)

      recordResult(
        action,
        second.status,
        increment === 1,
        increment === 1
          ? 'Se enviaron dos copias y el contador aumentó una vez.'
          : increment === 0
            ? 'Se enviaron dos copias; no hubo incremento observable. El eventId fijo pudo haberse procesado antes.'
            : `Se enviaron dos copias, pero el contador aumentó ${increment} veces.`
      )
      if (first.status !== second.status) {
        setFeedback((current) => ({ ...current, message: `${current.message} Respuestas: ${first.status} y ${second.status}.` }))
      }
      notifyDataChanged()
    } catch (error) {
      recordError(action, error)
    } finally {
      setTestingEvent(false)
    }
  }

  async function handleEnviarRaw(action, key, payload) {
    setTestingEvent(true)
    try {
      const response = await kafkaAdminService.enviarEvento({ key, payload })
      recordResult(action, response.status, true, successMessage(response.data, 'Mensaje aceptado por Kafka.'))
      notifyDataChanged()
    } catch (error) {
      recordError(action, error)
    } finally {
      setTestingEvent(false)
    }
  }

  async function handleCrearTopico(event) {
    event.preventDefault()
    setTopicAction('create')
    try {
      const request = {
        name: newTopic.name,
        partitions: Number(newTopic.partitions),
        replicationFactor: Number(newTopic.replicationFactor),
        configs: newTopic.retentionMs ? { 'retention.ms': String(newTopic.retentionMs) } : {},
      }
      const response = await kafkaAdminService.crearTopico(request)
      recordResult('Crear tópico', response.status, true, `Tópico "${response.data.name}" creado.`)
      setNewTopic(INITIAL_TOPIC)
      setTopicsRefreshKey((current) => current + 1)
      notifyDataChanged()
    } catch (error) {
      recordError('Crear tópico', error, true)
    } finally {
      setTopicAction('')
    }
  }

  async function handleActualizarTopico(event, topic) {
    event.preventDefault()
    const edit = topicEdits[topic.name] || {}
    const request = {}
    if (edit.partitions) request.partitions = Number(edit.partitions)
    if (edit.retentionMs) request.configs = { 'retention.ms': String(edit.retentionMs) }
    if (Object.keys(request).length === 0) {
      setTopicError('Indica nuevas particiones o un nuevo retention.ms.')
      return
    }

    setTopicAction(topic.name)
    try {
      const response = await kafkaAdminService.actualizarTopico(topic.name, request)
      recordResult(`Actualizar tópico ${topic.name}`, response.status, true, `Tópico "${topic.name}" actualizado.`)
      setTopicEdits((current) => ({ ...current, [topic.name]: { partitions: '', retentionMs: '' } }))
      setTopicsRefreshKey((current) => current + 1)
      notifyDataChanged()
    } catch (error) {
      recordError(`Actualizar tópico ${topic.name}`, error, true)
    } finally {
      setTopicAction('')
    }
  }

  async function handleEliminarTopico(topic) {
    if (!window.confirm(`¿Eliminar el tópico "${topic.name}"? El backend rechazará con 403 los tópicos protegidos.`)) {
      return
    }

    setTopicAction(topic.name)
    try {
      const response = await kafkaAdminService.eliminarTopico(topic.name)
      recordResult(
        `Eliminar tópico ${topic.name}`,
        response.status,
        true,
        successMessage(response.data, `Tópico "${topic.name}" eliminado.`)
      )
      setTopicsRefreshKey((current) => current + 1)
      notifyDataChanged()
    } catch (error) {
      recordError(`Eliminar tópico ${topic.name}`, error, true)
    } finally {
      setTopicAction('')
    }
  }

  return (
    <section className="kafka-lab card">
      <div className="page__heading">
        <h2>Laboratorio de escenarios Kafka</h2>
        <p>Ejecuta los flujos de reservas, idempotencia, DLT y administración de tópicos.</p>
      </div>

      <div className="tabs kafka-lab__tabs" role="tablist" aria-label="Secciones del laboratorio Kafka">
        <button
          className={`tab ${activeTab === 'reservas' ? 'tab--active' : ''}`}
          type="button"
          role="tab"
          aria-selected={activeTab === 'reservas'}
          onClick={() => setActiveTab('reservas')}
        >
          Reservas y eventos
        </button>
        <button
          className={`tab ${activeTab === 'topics' ? 'tab--active' : ''}`}
          type="button"
          role="tab"
          aria-selected={activeTab === 'topics'}
          onClick={() => setActiveTab('topics')}
        >
          Tópicos Kafka
        </button>
      </div>

      {feedback && (
        <p className={feedback.success ? 'admin-action-feedback' : 'form-error'} role="status">
          HTTP {feedback.status} · {feedback.message}
        </p>
      )}

      {activeTab === 'reservas' ? (
        <div className="kafka-lab__content">
          <section className="kafka-lab__section">
            <h3>Crear reserva con equipos</h3>
            <form className="kafka-lab__form" onSubmit={handleCrearReserva}>
              <div className="kafka-lab__form-row">
                <label className="field">
                  <span>Fecha</span>
                  <input
                    type="date"
                    required
                    min={new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)}
                    value={reservation.reservationDate}
                    onChange={(event) =>
                      setReservation((current) => ({ ...current, reservationDate: event.target.value }))
                    }
                  />
                </label>
                <label className="field">
                  <span>Nombre del evento</span>
                  <input
                    required
                    value={reservation.eventName}
                    onChange={(event) => setReservation((current) => ({ ...current, eventName: event.target.value }))}
                  />
                </label>
                <label className="field">
                  <span>Descripción</span>
                  <input
                    value={reservation.description}
                    onChange={(event) => setReservation((current) => ({ ...current, description: event.target.value }))}
                  />
                </label>
              </div>

              <fieldset className="kafka-lab__equipment-options">
                <legend>Equipos y horas de uso</legend>
                {equipos.length === 0 && <p className="muted">Crea primero un equipo para probar reservas.</p>}
                {equipos.map((equipo) => (
                  <label className="kafka-lab__equipment-option" key={equipo.id}>
                    <input
                      type="checkbox"
                      checked={Boolean(selectedEquipos[equipo.id])}
                      onChange={() => toggleEquipo(equipo.id)}
                    />
                    <span>{equipo.nombre}</span>
                    <input
                      type="number"
                      min="0.1"
                      step="0.1"
                      aria-label={`Horas de uso de ${equipo.nombre}`}
                      value={selectedEquipos[equipo.id] || '1'}
                      disabled={!selectedEquipos[equipo.id]}
                      onChange={(event) =>
                        setSelectedEquipos((current) => ({ ...current, [equipo.id]: event.target.value }))
                      }
                    />
                  </label>
                ))}
              </fieldset>

              {formError && <p className="form-error" role="alert">{formError}</p>}
              <button className="btn btn--primary" type="submit" disabled={submitting || equipos.length === 0}>
                {submitting ? 'Enviando…' : 'Crear reserva'}
              </button>
            </form>
          </section>

          <section className="kafka-lab__section">
            <h3>Cancelar reserva reciente (evento REVERSA)</h3>
            {reservasActivas.length === 0 ? (
              <p className="muted">No hay reservas confirmadas para cancelar.</p>
            ) : (
              <form className="kafka-lab__inline-form" onSubmit={handleCancelarReserva}>
                <label className="field">
                  <span>Reserva confirmada</span>
                  <select value={reservaCancelarId} onChange={(event) => setReservaCancelarId(event.target.value)} required>
                    <option value="">Selecciona una reserva</option>
                    {reservasActivas.map((item) => (
                      <option key={item.id} value={item.id}>
                        #{item.id} · {item.fechaReserva} · {item.nombreEvento}
                      </option>
                    ))}
                  </select>
                </label>
                <button className="btn btn--danger" type="submit" disabled={submitting || !reservaCancelarId}>
                  Cancelar reserva
                </button>
              </form>
            )}
          </section>

          <section className="kafka-lab__section">
            <h3>Pruebas de idempotencia y Dead Letter Topic</h3>
            <p className="muted">
              Los envíos raw se aceptan con HTTP 202. La llegada a la DLT se procesa en segundo plano y puede verificarse
              en Kafka-UI.
            </p>
            <div className="kafka-lab__inline-form">
              <label className="field">
                <span>Equipo para la prueba de idempotencia</span>
                <select value={equipoEventoId} onChange={(event) => setEquipoEventoId(event.target.value)}>
                  <option value="">Selecciona un equipo</option>
                  {equipos.map((equipo) => (
                    <option key={equipo.id} value={equipo.id}>
                      {equipo.nombre} (#{equipo.id})
                    </option>
                  ))}
                </select>
              </label>
              <button
                className="btn btn--primary"
                type="button"
                onClick={handleTestIdempotency}
                disabled={testingEvent || !equipoEventoId}
              >
                {testingEvent ? 'Probando…' : 'Probar idempotencia'}
              </button>
            </div>
            <div className="kafka-lab__button-row">
              <button
                className="btn btn--ghost"
                type="button"
                disabled={testingEvent}
                onClick={() => handleEnviarRaw('DLT: JSON corrupto', '1', 'esto-no-es-json')}
              >
                Probar DLT · JSON corrupto
              </button>
              <button
                className="btn btn--ghost"
                type="button"
                disabled={testingEvent}
                onClick={() =>
                  handleEnviarRaw(
                    'DLT: equipo inexistente',
                    '999',
                    '{"eventId":"demo-dlt-1","tipo":"USO","equipoId":999,"horas":1.0,"fecha":"2026-12-20"}'
                  )
                }
              >
                Probar DLT · Equipo #999
              </button>
            </div>
          </section>
        </div>
      ) : (
        <div className="kafka-lab__content">
          <div className="kafka-lab__section-heading">
            <div>
              <h3>Administración de tópicos</h3>
              <p className="muted">La lista incluye tópicos protegidos para probar la respuesta 403 del backend.</p>
            </div>
            <button
              className="btn btn--ghost btn--small"
              type="button"
              onClick={() => setTopicsRefreshKey((current) => current + 1)}
              disabled={loadingTopics}
            >
              {loadingTopics ? 'Actualizando…' : 'Actualizar tópicos'}
            </button>
          </div>

          {topicError && <p className="form-error" role="alert">{topicError}</p>}
          {loadingTopics && <p className="muted" role="status">Cargando tópicos…</p>}

          <form className="kafka-lab__form kafka-lab__topic-create" onSubmit={handleCrearTopico}>
            <h4>Crear tópico</h4>
            <div className="kafka-lab__form-row">
              <label className="field">
                <span>Nombre</span>
                <input
                  required
                  pattern="[a-zA-Z0-9._-]{1,200}"
                  value={newTopic.name}
                  onChange={(event) => setNewTopic((current) => ({ ...current, name: event.target.value }))}
                />
              </label>
              <label className="field">
                <span>Particiones</span>
                <input
                  type="number"
                  min="1"
                  max="50"
                  required
                  value={newTopic.partitions}
                  onChange={(event) => setNewTopic((current) => ({ ...current, partitions: event.target.value }))}
                />
              </label>
              <label className="field">
                <span>Factor de replicación</span>
                <input
                  type="number"
                  min="1"
                  max="5"
                  required
                  value={newTopic.replicationFactor}
                  onChange={(event) =>
                    setNewTopic((current) => ({ ...current, replicationFactor: event.target.value }))
                  }
                />
              </label>
              <label className="field">
                <span>retention.ms</span>
                <input
                  type="number"
                  min="1"
                  value={newTopic.retentionMs}
                  onChange={(event) => setNewTopic((current) => ({ ...current, retentionMs: event.target.value }))}
                />
              </label>
            </div>
            <div className="kafka-lab__button-row">
              <button
                className="btn btn--ghost btn--small"
                type="button"
                onClick={() => setNewTopic({ name: 'prueba-admin', partitions: '2', replicationFactor: '3', retentionMs: '86400000' })}
              >
                Usar preset prueba-admin
              </button>
              <button className="btn btn--primary btn--small" type="submit" disabled={topicAction === 'create'}>
                {topicAction === 'create' ? 'Creando…' : 'Crear tópico'}
              </button>
            </div>
          </form>

          {!loadingTopics && topics.length === 0 && !topicError && (
            <p className="empty-state">No se encontraron tópicos.</p>
          )}
          <div className="kafka-topic-list">
            {topics.map((topic) => {
              const edit = topicEdits[topic.name] || { partitions: '', retentionMs: '' }
              return (
                <article className="kafka-topic" key={topic.name}>
                  <div className="kafka-topic__heading">
                    <div>
                      <h4>{topic.name}</h4>
                      <p>
                        Particiones: {topic.partitions} · Réplicas: {topic.replicationFactor}
                      </p>
                    </div>
                    <button
                      className="btn btn--danger btn--small"
                      type="button"
                      disabled={topicAction === topic.name}
                      onClick={() => handleEliminarTopico(topic)}
                    >
                      Eliminar
                    </button>
                  </div>
                  <p className="kafka-topic__configs">
                    {Object.entries(topic.configs || {}).length > 0
                      ? Object.entries(topic.configs).map(([key, value]) => `${key}: ${value}`).join(' · ')
                      : 'Sin configuración dinámica del tópico.'}
                  </p>
                  <form className="kafka-topic__edit" onSubmit={(event) => handleActualizarTopico(event, topic)}>
                    <label className="maintenance-filter">
                      <span>Aumentar a particiones</span>
                      <input
                        type="number"
                        min={topic.partitions + 1}
                        max="50"
                        value={edit.partitions}
                        onChange={(event) =>
                          setTopicEdits((current) => ({
                            ...current,
                            [topic.name]: { ...edit, partitions: event.target.value },
                          }))
                        }
                      />
                    </label>
                    <label className="maintenance-filter">
                      <span>Nuevo retention.ms</span>
                      <input
                        type="number"
                        min="1"
                        value={edit.retentionMs}
                        onChange={(event) =>
                          setTopicEdits((current) => ({
                            ...current,
                            [topic.name]: { ...edit, retentionMs: event.target.value },
                          }))
                        }
                      />
                    </label>
                    <button className="btn btn--ghost btn--small" type="submit" disabled={topicAction === topic.name}>
                      Aplicar cambios
                    </button>
                  </form>
                </article>
              )
            })}
          </div>
        </div>
      )}
    </section>
  )
}
