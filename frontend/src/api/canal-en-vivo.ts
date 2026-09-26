import type { components } from './generado/esquema'

export type ListadoSectores = { sectores: components['schemas']['SectorRespuesta'][]; generadoEn: string }
export type EstadoCanal = 'en-vivo' | 'reconectando' | 'sondeando' | 'pausado' | 'sin-red'
export interface LecturaCanal { listado: ListadoSectores | null; estado: EstadoCanal; error: boolean }
export const CIERRE_PESTANA_OCULTA_MS = 15_000

export interface EntornoCanal {
  fetch: typeof fetch
  conectar: typeof fetch
  ahora: () => number
  aleatorio: () => number
  programar: (accion: () => void, ms: number) => ReturnType<typeof setTimeout>
  cancelar: (id: ReturnType<typeof setTimeout>) => void
  visible: () => boolean
  enLinea: () => boolean
  observar: (accion: () => void) => () => void
  recuperar: () => ListadoSectores | null
  guardar: (listado: ListadoSectores) => void
}

export function parserSse(aviso: () => void, retry: (ms: number) => void) {
  let pendiente = '', evento = '', datos = false
  return (fragmento: string) => {
    pendiente += fragmento
    while (true) {
      const fin = pendiente.search(/[\r\n]/)
      if (fin < 0 || (pendiente[fin] === '\r' && fin === pendiente.length - 1)) break
      const linea = pendiente.slice(0, fin)
      const salto = pendiente[fin] === '\r' && pendiente[fin + 1] === '\n' ? 2 : 1
      pendiente = pendiente.slice(fin + salto)
      if (linea === '') {
        if (evento === 'sectores' && datos) aviso()
        evento = ''; datos = false
      } else if (!linea.startsWith(':')) {
        const separador = linea.indexOf(':')
        const campo = separador < 0 ? linea : linea.slice(0, separador)
        const valor = separador < 0 ? '' : linea.slice(separador + 1).replace(/^ /, '')
        if (campo === 'event') evento = valor
        if (campo === 'data') datos = true
        if (campo === 'retry' && /^\d+$/.test(valor)) retry(Number(valor))
      }
    }
  }
}

function esperaServidor(respuesta: Response, ahora: number) {
  const valor = respuesta.headers.get('Retry-After')
  if (!valor) return 30_000
  const segundos = Number(valor)
  return Number.isFinite(segundos) ? Math.max(0, segundos * 1000) : Math.max(0, Date.parse(valor) - ahora) || 30_000
}

export function crearCanal(entorno: EntornoCanal) {
  let lectura: LecturaCanal = { listado: entorno.recuperar(), estado: 'pausado', error: false }
  const oyentes = new Set<() => void>()
  let activo = false, conectado = false, ultimaConsulta = -Infinity, intento = 0, retryServidor = 0, bloqueoHasta = 0, reconectarHasta = 0
  let conexion: AbortController | null = null, consulta: AbortController | null = null
  let temporizadorConsulta: ReturnType<typeof setTimeout> | undefined
  let temporizadorConexion: ReturnType<typeof setTimeout> | undefined
  let temporizadorCierre: ReturnType<typeof setTimeout> | undefined
  let temporizadorSondeo: ReturnType<typeof setTimeout> | undefined
  let dejarDeObservar: (() => void) | undefined
  const permitido = () => activo && entorno.visible() && entorno.enLinea()
  const publicar = (cambio: Partial<LecturaCanal>) => {
    lectura = { ...lectura, ...cambio }; oyentes.forEach((accion) => accion())
  }
  function cancelarPeticiones() {
    for (const id of [temporizadorConsulta, temporizadorConexion, temporizadorSondeo]) if (id !== undefined) entorno.cancelar(id)
    temporizadorConsulta = temporizadorConexion = temporizadorSondeo = undefined
    consulta?.abort(); consulta = null
  }
  function actualizar(retardo = 0, reintento = 0) {
    if (!permitido() || temporizadorConsulta !== undefined || consulta) return
    const espera = Math.max(retardo, ultimaConsulta + 5000 - entorno.ahora(), bloqueoHasta - entorno.ahora())
    temporizadorConsulta = entorno.programar(() => {
      temporizadorConsulta = undefined
      if (!permitido()) return
      ultimaConsulta = entorno.ahora()
      const control = new AbortController(); consulta = control
      void entorno.fetch('/api/sectores', { signal: control.signal }).then(async (respuesta) => {
        if (!respuesta.ok) {
          if (respuesta.status === 429) bloqueoHasta = entorno.ahora() + esperaServidor(respuesta, entorno.ahora())
          throw new Error('lectura')
        }
        const listado = await respuesta.json() as ListadoSectores
        if (!Array.isArray(listado.sectores) || typeof listado.generadoEn !== 'string') throw new Error('listado')
        if (control.signal.aborted || !permitido()) return
        entorno.guardar(listado); publicar({ listado, error: false })
      }).catch(() => {
        if (!control.signal.aborted) {
          publicar({ error: true })
          if (reintento < 3) { consulta = null; actualizar(1000 * 2 ** reintento * (0.5 + entorno.aleatorio()), reintento + 1) }
        }
      }).finally(() => { if (consulta === control) consulta = null })
    }, Math.max(0, espera))
  }
  function sondear() {
    if (!permitido() || temporizadorSondeo !== undefined) return
    temporizadorSondeo = entorno.programar(() => { temporizadorSondeo = undefined; actualizar(); sondear() }, 30_000)
  }
  function reconectar(ms: number) {
    if (!permitido()) return
    reconectarHasta = entorno.ahora() + ms
    temporizadorConexion = entorno.programar(() => { temporizadorConexion = undefined; void conectar() }, ms)
  }
  async function conectar() {
    if (!permitido() || conexion) return
    const control = new AbortController(); conexion = control
    try {
      const respuesta = await entorno.conectar('/api/sectores/stream', { headers: { Accept: 'text/event-stream' }, signal: control.signal })
      if (control.signal.aborted || !permitido()) return
      if (respuesta.status === 429) {
        publicar({ estado: 'sondeando' }); sondear()
        reconectar(esperaServidor(respuesta, entorno.ahora())); return
      }
      if (!respuesta.ok || !respuesta.body || !respuesta.headers.get('Content-Type')?.includes('text/event-stream')) throw new Error('canal')
      if (temporizadorSondeo !== undefined) entorno.cancelar(temporizadorSondeo)
      temporizadorSondeo = undefined
      conectado = true; publicar({ estado: 'en-vivo' }); intento = 0
      const procesar = parserSse(() => actualizar(entorno.aleatorio() * 3000), (ms) => { retryServidor = ms })
      const lector = respuesta.body.getReader(), decodificador = new TextDecoder()
      try {
        while (!control.signal.aborted) {
          const { value, done } = await lector.read()
          if (done) break
          procesar(decodificador.decode(value, { stream: true }))
        }
      } finally { await lector.cancel().catch(() => undefined); lector.releaseLock() }
      if (!control.signal.aborted) throw new Error('fin-normal')
    } catch {
      if (!control.signal.aborted && permitido()) {
        publicar({ estado: 'reconectando' })
        const espera = Math.min(60_000, 1000 * 2 ** Math.min(intento++, 10) * (0.5 + entorno.aleatorio()))
        reconectar(Math.max(retryServidor, espera))
      }
    } finally { control.abort(); if (conexion === control) { conexion = null; conectado = false } }
  }
  function cambiarEntorno() {
    if (!activo) return
    if (!entorno.visible() || !entorno.enLinea()) {
      cancelarPeticiones()
      publicar({ estado: entorno.enLinea() ? 'pausado' : 'sin-red' })
      if (!entorno.enLinea()) { conexion?.abort(); conexion = null; conectado = false }
      else if (temporizadorCierre === undefined) temporizadorCierre = entorno.programar(() => {
        temporizadorCierre = undefined; conexion?.abort(); conexion = null; conectado = false
      }, CIERRE_PESTANA_OCULTA_MS)
    } else {
      if (temporizadorCierre !== undefined) entorno.cancelar(temporizadorCierre)
      temporizadorCierre = undefined
      actualizar()
      if (conexion) publicar({ estado: conectado ? 'en-vivo' : 'reconectando' })
      else { publicar({ estado: 'reconectando' }); reconectar(Math.max(0, reconectarHasta - entorno.ahora())) }
    }
  }
  return {
    lectura: () => lectura,
    suscribir: (accion: () => void) => { oyentes.add(accion); return () => { oyentes.delete(accion) } },
    actualizar: () => actualizar(),
    iniciar: () => { if (activo) return; activo = true; dejarDeObservar = entorno.observar(cambiarEntorno); cambiarEntorno() },
    detener: () => {
      activo = false; cancelarPeticiones(); conexion?.abort(); conexion = null; conectado = false
      if (temporizadorCierre !== undefined) entorno.cancelar(temporizadorCierre)
      temporizadorCierre = undefined; dejarDeObservar?.(); publicar({ estado: 'pausado' })
    },
  }
}

export function entornoNavegador(): EntornoCanal {
  return {
    fetch: window.fetch.bind(window), conectar: window.fetch.bind(window), ahora: Date.now, aleatorio: Math.random,
    programar: (accion, ms) => setTimeout(accion, ms), cancelar: clearTimeout,
    visible: () => document.visibilityState === 'visible', enLinea: () => navigator.onLine,
    observar: (accion) => {
      document.addEventListener('visibilitychange', accion); window.addEventListener('online', accion); window.addEventListener('offline', accion)
      return () => { document.removeEventListener('visibilitychange', accion); window.removeEventListener('online', accion); window.removeEventListener('offline', accion) }
    },
    recuperar: () => { try { return JSON.parse(localStorage.getItem('aguavigia-listado') ?? 'null') as ListadoSectores | null } catch { return null } },
    guardar: (listado) => { try { localStorage.setItem('aguavigia-listado', JSON.stringify(listado)) } catch { /* La lectura en memoria sigue disponible con almacenamiento bloqueado. */ } },
  }
}
