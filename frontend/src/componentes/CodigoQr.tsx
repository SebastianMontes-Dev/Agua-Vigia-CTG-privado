import { useMemo } from 'react'
import { create } from 'qrcode'
import estilos from './CodigoQr.module.css'

const MARGEN = 4

/** El QR se arma en el navegador: el secreto del TOTP no sale a ningún servicio (plan §3). */
export function trazoQr(texto: string): { lado: number; trazo: string } {
  const { modules } = create(texto, { errorCorrectionLevel: 'M' })
  let trazo = ''
  for (let fila = 0; fila < modules.size; fila++) {
    for (let columna = 0; columna < modules.size; columna++) {
      if (modules.get(fila, columna)) trazo += `M${columna + MARGEN} ${fila + MARGEN}h1v1h-1z`
    }
  }
  return { lado: modules.size + 2 * MARGEN, trazo }
}

/** Sin texto alternativo: la clave escrita junto al QR es su equivalente para quien no puede escanearlo. */
export function CodigoQr({ texto }: { texto: string }) {
  const { lado, trazo } = useMemo(() => trazoQr(texto), [texto])
  return (
    <svg className={estilos.qr} viewBox={`0 0 ${lado} ${lado}`} aria-hidden="true" shapeRendering="crispEdges">
      <rect className={estilos.fondo} width={lado} height={lado} />
      <path className={estilos.modulo} d={trazo} />
    </svg>
  )
}
