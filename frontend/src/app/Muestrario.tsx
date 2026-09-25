import { useState, useSyncExternalStore } from 'react'
import { ORDEN_ESTADOS, presentarEstado } from '../dominio/estados'
import { contraste } from '../estilos/contraste'
import { aplicarTema, leerPreferenciaTema, type PreferenciaTema } from './tema'
import estilos from './Muestrario.module.css'

const OPCIONES_TEMA: { valor: PreferenciaTema; texto: string }[] = [
  { valor: 'sistema', texto: 'Como el sistema' },
  { valor: 'claro', texto: 'Claro' },
  { valor: 'oscuro', texto: 'Oscuro' },
]

const PALETA_BASE = [
  ['--acento', 'Acento'],
  ['--acento-vivo', 'Acento vivo'],
  ['--acento-suave', 'Acento suave'],
  ['--tinta', 'Tinta'],
  ['--tinta-secundaria', 'Tinta secundaria'],
  ['--tinta-terciaria', 'Tinta terciaria'],
  ['--linea', 'Línea'],
  ['--superficie', 'Superficie'],
  ['--fondo', 'Fondo'],
] as const

// Las combinaciones en que --acento puede ir como texto (guia-frontend.md §2.2), más el botón principal.
const COMBINACIONES_ACENTO = [
  ['--fondo', 'Acento sobre fondo'],
  ['--superficie', 'Acento sobre superficie'],
  ['--acento-suave', 'Acento sobre acento suave'],
] as const

type TemaEnUso = 'claro' | 'oscuro'

interface LecturaTokens {
  tema: TemaEnUso
  color: (variable: string) => string
}

// El tema lo decide el atributo data-theme o, sin él, la preferencia del sistema: se escuchan los dos.
function suscribirTema(avisar: () => void): () => void {
  const consulta = window.matchMedia?.('(prefers-color-scheme: dark)')
  consulta?.addEventListener('change', avisar)
  const observador = new MutationObserver(avisar)
  observador.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  return () => {
    consulta?.removeEventListener('change', avisar)
    observador.disconnect()
  }
}

function leerEsquema(): string {
  return getComputedStyle(document.documentElement).colorScheme
}

function useLecturaTokens(): LecturaTokens | null {
  const esquema = useSyncExternalStore(suscribirTema, leerEsquema)
  if (esquema !== 'light' && esquema !== 'dark') return null
  const estilo = getComputedStyle(document.documentElement)
  return {
    tema: esquema === 'dark' ? 'oscuro' : 'claro',
    color: (variable) => estilo.getPropertyValue(variable).trim(),
  }
}

const formatoRazon = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function razon(lectura: LecturaTokens | null, texto: string, fondo: string): string | null {
  const a = lectura?.color(texto)
  const b = lectura?.color(fondo)
  if (!a || !b) return null
  return `${formatoRazon.format(contraste(a, b))}:1`
}

export function Muestrario() {
  const [tema, setTema] = useState<PreferenciaTema>(leerPreferenciaTema)
  const lectura = useLecturaTokens()
  const textoSobreAcento = lectura?.tema === 'oscuro' ? '--fondo' : '--superficie'

  function cambiarTema(preferencia: PreferenciaTema) {
    setTema(preferencia)
    aplicarTema(preferencia)
  }

  const estados = [...ORDEN_ESTADOS.map((estado) => presentarEstado(estado)), presentarEstado(null)]

  return (
    <main className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.antetitulo}>Andamiaje · F0</p>
        <h1>AguaVigía CTG</h1>
        <p className={estilos.bajada}>
          Tokens de <code>DESIGN.md</code> en los dos temas. Esta página se reemplaza por el mapa en F2.
        </p>
      </header>

      <fieldset className={estilos.tema}>
        <legend>Tema</legend>
        {OPCIONES_TEMA.map((opcion) => (
          <label key={opcion.valor} className={estilos.opcion}>
            <input
              type="radio"
              name="tema"
              value={opcion.valor}
              checked={tema === opcion.valor}
              onChange={() => cambiarTema(opcion.valor)}
            />
            {opcion.texto}
          </label>
        ))}
      </fieldset>

      <section aria-labelledby="titulo-estados">
        <h2 id="titulo-estados">Estados del servicio</h2>
        <ul className={estilos.estados}>
          {estados.map((estado) => (
            <li key={estado.clave} className={estilos.estado}>
              <span
                aria-hidden="true"
                className={estado.variableColor ? estilos.muestra : `${estilos.muestra} ${estilos.trama}`}
                style={estado.variableColor ? { background: `var(${estado.variableColor})` } : undefined}
              />
              <span>{estado.texto}</span>
              <code className={estilos.token}>{estado.variableColor ?? 'trama'}</code>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="titulo-acento">
        <h2 id="titulo-acento">Acento y combinaciones permitidas</h2>
        <p className={estilos.secundario}>
          Contraste medido en esta pantalla{lectura ? `, tema ${lectura.tema}` : ''}. El texto normal exige 4,5:1.
        </p>
        <ul className={estilos.combinaciones}>
          {COMBINACIONES_ACENTO.map(([fondo, nombre]) => (
            <li key={fondo} className={estilos.combinacion} style={{ background: `var(${fondo})` }}>
              <span className={estilos.textoAcento}>{nombre}</span>
              <span className={estilos.razon}>{razon(lectura, '--acento', fondo)}</span>
            </li>
          ))}
          <li className={estilos.combinacion} style={{ background: 'var(--superficie)' }}>
            <span className={estilos.relleno}>Botón principal</span>
            <span className={estilos.razon}>{razon(lectura, textoSobreAcento, '--acento')}</span>
          </li>
        </ul>
      </section>

      <section aria-labelledby="titulo-base">
        <h2 id="titulo-base">Paleta base</h2>
        <ul className={estilos.paleta}>
          {PALETA_BASE.map(([variable, nombre]) => (
            <li key={variable} className={estilos.color}>
              <span aria-hidden="true" className={estilos.muestra} style={{ background: `var(${variable})` }} />
              <span>{nombre}</span>
              <code className={estilos.token}>{variable}</code>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="titulo-tipografia" className={estilos.tipografia}>
        <h2 id="titulo-tipografia">Tipografía</h2>
        <p className={estilos.secundario}>Texto de muestra, no describe ningún barrio real.</p>
        <p className={estilos.display}>Nombre del barrio · Estado del agua</p>
        <p>Texto corrido de lectura: así se ve un párrafo de ayuda en el cuerpo de la página.</p>
        <p className={estilos.secundario}>
          Texto secundario · <span className="mono">RF001</span>
        </p>
      </section>
    </main>
  )
}
