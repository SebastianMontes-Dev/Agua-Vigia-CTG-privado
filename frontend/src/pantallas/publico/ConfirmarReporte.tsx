import { Link, useParams } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Button } from 'react-aria-components'
import { api } from '../../api/cliente'
import { confirmarReporte, type Reporte, type ResultadoConfirmacion, type TipoReporte } from '../../api/reportes'
import { MarcaRecibido } from '../../componentes/MarcaRecibido'
import { nombreLegible } from '../../dominio/sectores'
import botones from '../../componentes/Botones.module.css'
import pagina from './Pagina.module.css'
import estilos from './ConfirmarReporte.module.css'

const LO_QUE_DICE: Record<TipoReporte, string> = {
  SIN_AGUA: 'no llega agua',
  PRESION_BAJA: 'llega poca agua',
  SERVICIO_RESTABLECIDO: 'ya volvió el agua',
}

const vecinos = new Intl.PluralRules('es-CO')

function mensaje(resultado: Exclude<ResultadoConfirmacion, { tipo: 'confirmado' }>): string {
  switch (resultado.tipo) {
    case 'no-disponible': return 'Este reporte no está disponible. Puede que el enlace esté incompleto o que el reporte ya no cuente.'
    case 'sin-red': return 'No pudimos enviar tu confirmación. Cuando recuperes la conexión, inténtalo otra vez.'
    // Confirmar dos veces con el mismo dispositivo cuenta una sola: reintentar a mano no duplica nada.
    case 'incierto': return 'No pudimos saber si se recibió tu confirmación. Puedes enviarla otra vez: si ya llegó, no se cuenta dos veces.'
    case 'esperar': return resultado.segundos
      ? `Espera antes de volver a intentarlo. Podrás hacerlo en unos ${resultado.segundos} segundos.`
      : 'Espera antes de volver a intentarlo.'
    case 'rechazado': return resultado.mensaje
    case 'fallo': return 'No pudimos registrar tu confirmación. Inténtalo otra vez en un momento.'
  }
}

/** El nombre del barrio sale del sector que devolvió la confirmación; sin él, se dice sin barrio. */
function useNombreSector(sectorId: string | undefined) {
  return useQuery({
    queryKey: ['sector', sectorId],
    enabled: Boolean(sectorId),
    queryFn: async () => {
      const { data } = await api.GET('/api/sectores/{id}', { params: { path: { id: sectorId ?? '' } } })
      return data?.nombre ? nombreLegible(data.nombre) : null
    },
    staleTime: Infinity,
  })
}

function Confirmado({ reporte }: { reporte: Reporte }) {
  const { data: barrio } = useNombreSector(reporte.sectorId)
  const dice = LO_QUE_DICE[reporte.tipo as TipoReporte] as string | undefined
  const otros = reporte.confirmaciones ?? 0

  return (
    <div className={estilos.confirmado}>
      <MarcaRecibido />
      <h2 className={estilos.titulo}>Confirmación recibida</h2>
      <output className={estilos.resumen}>
        {dice && <>El reporte dice que {barrio ? <>en <strong>{barrio}</strong> </> : ''}{dice}. </>}
        {otros > 0 && (vecinos.select(otros) === 'one'
          ? 'Lo confirma 1 vecino además de quien lo envió.'
          : `Lo confirman ${otros} vecinos además de quien lo envió.`)}
      </output>
      <p className={estilos.nota}>
        Cada dispositivo cuenta una sola vez. El estado del barrio en el mapa lo decide el consenso de los reportes.
      </p>
      {reporte.sectorId && (
        <Link to="/sectores/$id" params={{ id: reporte.sectorId }} className={botones.secundario}>
          Ver {barrio ?? 'el barrio'} en el mapa
        </Link>
      )}
    </div>
  )
}

/** RF038 (guía §5.1): abrir el enlace no confirma nada; se confirma al tocar, y no hay GET público del reporte. */
export function ConfirmarReporte() {
  const { id } = useParams({ from: '/publico/confirmar/$id' })
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoConfirmacion | null>(null)

  async function confirmar() {
    if (enviando) return
    setEnviando(true)
    setResultado(null)
    setResultado(await confirmarReporte(id))
    setEnviando(false)
  }

  return (
    <div className={`${pagina.pagina} ${estilos.columna}`}>
      <h1 className={pagina.titular}>Confirmar reporte</h1>
      {resultado?.tipo === 'confirmado' ? (
        <Confirmado reporte={resultado.reporte} />
      ) : resultado?.tipo === 'no-disponible' ? (
        <div className={estilos.confirmado}>
          <p role="alert">{mensaje(resultado)}</p>
          <Link to="/" className={pagina.enlace}>Ir al mapa</Link>
        </div>
      ) : (
        <>
          <p className={pagina.entrada}>
            Un vecino te compartió su reporte sobre el agua. Si en tu casa pasa lo mismo, confírmalo con un toque.
          </p>
          <div className={estilos.bloque}>
            <Button className={botones.principal} isDisabled={enviando} onPress={() => void confirmar()}>
              {enviando ? 'Enviando…' : <>Confirmar este reporte <span className={botones.flecha} aria-hidden="true">→</span></>}
            </Button>
            <p className={estilos.nota}>No hace falta cuenta. Si confirmas dos veces desde este dispositivo, cuenta una sola.</p>
            {resultado && <p className={estilos.alerta} role="alert">{mensaje(resultado)}</p>}
          </div>
        </>
      )}
    </div>
  )
}
