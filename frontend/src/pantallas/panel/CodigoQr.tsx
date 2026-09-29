import { encode } from 'uqr'
import { useMemo } from 'react'
import estilos from './Formulario.module.css'

/** Puntos de la matriz como un solo trazado: un `M x y h1 v1 h-1 z` por módulo oscuro. */
export function trazadoDeQr(matriz: readonly (readonly boolean[])[]): string {
  const partes: string[] = []
  matriz.forEach((fila, y) => fila.forEach((oscuro, x) => { if (oscuro) partes.push(`M${x} ${y}h1v1h-1z`) }))
  return partes.join('')
}

export function svgDeQr(matriz: readonly (readonly boolean[])[]): string {
  const lado = matriz.length
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${lado} ${lado}" shape-rendering="crispEdges">`
    + `<rect width="${lado}" height="${lado}" fill="white"/><path d="${trazadoDeQr(matriz)}" fill="black"/></svg>`
}

/**
 * El QR se dibuja aquí mismo (`uqr`) y viaja como imagen `data:`: el secreto del segundo factor nunca sale a un generador
 * de terceros ni queda en una URL.
 */
export function CodigoQr({ contenido, titulo }: { contenido: string; titulo: string }) {
  const origen = useMemo(
    () => `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgDeQr(encode(contenido, { ecc: 'M', border: 2 }).data))}`,
    [contenido],
  )
  return <img className={estilos.qr} src={origen} alt={titulo} width={240} height={240} />
}
