import '../estilos/editorial.css'
import { useEffect, useRef, useState } from 'react'
import { Button, Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components'
import type { components } from '../api/generado/esquema'
import { enviarReporte, pedir, mensajeFallo, type Reporte as DatoReporte } from '../api/ciudadano'
import { huellaDispositivo } from '../dominio/huella'
import type { Sector } from '../dominio/sectores'
export function HojaReporte({ abierto, cerrar, sector, sectores }: { abierto: boolean; cerrar: () => void; sector: Sector | undefined; sectores: Sector[] }) {
  const [manual, setManual] = useState(sector?.id ?? '')
  const [coordenada, setCoordenada] = useState<components['schemas']['CoordenadaDTO']>()
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState('')
  const [reporte, setReporte] = useState<DatoReporte | null>(null)
  const [foto, setFoto] = useState(false)
  const ocupado = useRef(false)
  const encabezado = useRef<HTMLHeadingElement>(null)
  useEffect(() => { if (reporte) encabezado.current?.focus() }, [reporte])
  async function enviar(tipo: string) {
    if (ocupado.current) return
    if (!navigator.onLine) { setError('Estás sin conexión. Tu reporte no se guardó. Vuelve a intentarlo a mano cuando tengas red.'); return }
    ocupado.current = true; setEnviando(true); setError('')
    try {
      const { dato } = await enviarReporte({ tipo, huella: await huellaDispositivo(), sectorId: manual || undefined, coordenada })
      setReporte(dato)
    } catch (fallo) { setError(mensajeFallo(fallo, true)) }
    finally { ocupado.current = false; setEnviando(false) }
  }
  function ubicar() {
    setError(''); setEnviando(true)
    if (!navigator.geolocation) { setError('Tu navegador no ofrece ubicación. Elige el barrio a mano.'); setEnviando(false); return }
    navigator.geolocation.getCurrentPosition((posicion) => {
      setCoordenada({ latitud: posicion.coords.latitude, longitud: posicion.coords.longitude }); setEnviando(false)
    }, () => { setError('No pudimos obtener tu ubicación. Puedes elegir el barrio a mano.'); setEnviando(false) }, { timeout: 10_000, maximumAge: 60_000 })
  }
  async function adjuntar(archivo: File | undefined) {
    if (!archivo || !reporte?.id || ocupado.current) return
    if (archivo.size >= 10 * 1024 * 1024) { setError('La foto debe pesar menos de 10 MB. Tu reporte sigue guardado.'); return }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(archivo.type)) { setError('Elige una foto JPEG, PNG o WebP. Tu reporte sigue guardado.'); return }
    ocupado.current = true; setEnviando(true); setError('')
    const cuerpo = new FormData(); cuerpo.append('foto', archivo)
    try { await pedir(`/api/reportes/${encodeURIComponent(reporte.id)}/foto`, { method: 'POST', body: cuerpo }); setFoto(true) }
    catch (fallo) { setError(mensajeFallo(fallo, true, true)) }
    finally { ocupado.current = false; setEnviando(false) }
  }
  return <ModalOverlay isOpen={abierto} onOpenChange={(valor) => { if (!valor && !enviando) cerrar() }} isDismissable={!enviando} className="velo">
    <Modal className="modal-reporte"><Dialog aria-label="Reportar cómo está el agua">
      <div className="fila"><span className="rotulo">Reporte ciudadano</span><Button className="secundario" onPress={cerrar} isDisabled={enviando}>Cerrar</Button></div>
      <Heading slot="title" ref={encabezado} tabIndex={-1}>{reporte ? 'Tu reporte cuenta' : sector?.nombre ?? '¿Cómo está el agua?'}</Heading>
      {reporte ? <><output>Gracias. Tu reporte cuenta junto con el de tus vecinos</output><p>El mapa cambia cuando hay evidencia suficiente.</p>
        <label className="foto">{foto ? 'Foto adjunta' : 'Añadir una foto (opcional)'}<input type="file" accept="image/jpeg,image/png,image/webp" disabled={enviando || foto} onChange={(evento) => { void adjuntar(evento.target.files?.[0]) }} /></label>
        <p className="pequeno">JPEG, PNG o WebP. Menos de 10 MB. No necesitas una foto para reportar.</p><Button className="primario" isDisabled={enviando} onPress={cerrar}>Volver al mapa</Button></>
        : <><p>Sin cuenta. Cuenta lo que pasa ahora en tu barrio.</p>
          {(!sector || error) && <><label htmlFor="barrio-reporte">Barrio</label><select id="barrio-reporte" value={manual} onChange={(evento) => { setManual(evento.target.value); setCoordenada(undefined) }}><option value="">Elige un barrio</option>{sectores.map((elemento) => <option key={elemento.id} value={elemento.id}>{elemento.nombre}</option>)}</select><Button className="secundario" onPress={ubicar} isDisabled={enviando}>{coordenada ? 'Ubicación obtenida' : 'Usar mi ubicación (opcional)'}</Button></>}
          <div className="opciones-reporte">{[['SIN_AGUA', 'No hay agua'], ['PRESION_BAJA', 'Sale con poca presión'], ['SERVICIO_RESTABLECIDO', 'Ya volvió el agua']].map(([tipo, texto]) => <Button className="opcion" key={tipo} isDisabled={enviando || (!manual && !coordenada)} onPress={() => { void enviar(tipo!) }}>{texto}<span aria-hidden="true">→</span></Button>)}</div>
          <p className="pequeno">Hasta tres reportes por barrio cada 30 minutos. La ubicación es opcional.</p></>}
      {enviando && <output>Enviando…</output>}{error && <p role="alert" className="aviso">{error}</p>}
    </Dialog></Modal>
  </ModalOverlay>
}
export function Confirmar({ id }: { id: string }) {
  const [enviando, setEnviando] = useState(false), [terminado, setTerminado] = useState(false), [error, setError] = useState('')
  const ocupado = useRef(false)
  async function confirmar() {
    if (ocupado.current || terminado) return
    ocupado.current = true; setEnviando(true); setError('')
    try { await pedir(`/api/reportes/${encodeURIComponent(id)}/confirmar`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ huella: await huellaDispositivo() }) }); setTerminado(true) }
    catch (fallo) { setError(mensajeFallo(fallo, true)) }
    finally { ocupado.current = false; setEnviando(false) }
  }
  return <main id="contenido" tabIndex={-1} className="confirmar"><span className="rotulo">Reporte de un vecino</span><h1>¿También te pasa?</h1><p>Confirma solo si el reporte coincide con lo que ves. Abrir este enlace no confirma nada.</p>{terminado ? <output>Gracias. Tu confirmación quedó registrada.</output> : <Button className="primario" isDisabled={enviando} onPress={() => { void confirmar() }}>{enviando ? 'Confirmando…' : 'Confirmar reporte'}</Button>}{error && <p className="aviso" role="alert">{error}</p>}<a href="/">Volver al mapa</a></main>
}
