import '../estilos/editorial.css'
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
  ['--papel', 'Papel'],
  ['--superficie', 'Superficie'],
  ['--elevada', 'Elevada'],
  ['--tinta', 'Tinta'],
  ['--tinta-2', 'Tinta 2'],
  ['--tinta-3', 'Tinta 3 · nunca texto'],
  ['--linea', 'Línea'],
  ['--linea-suave', 'Línea suave'],
  ['--cardenillo', 'Cardenillo'],
  ['--sobre-cardenillo', 'Sobre cardenillo'],
  ['--cardenillo-suave', 'Cardenillo suave'],
  ['--laton', 'Latón'],
] as const

// Las combinaciones en que el cardenillo puede ir como texto (identidad.md §2), más el botón principal y el latón.
const COMBINACIONES_ACCION = [
  ['--papel', 'Cardenillo sobre papel'],
  ['--superficie', 'Cardenillo sobre superficie'],
  ['--cardenillo-suave', 'Cardenillo sobre cardenillo suave'],
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

  function cambiarTema(preferencia: PreferenciaTema) {
    setTema(preferencia)
    aplicarTema(preferencia)
  }

  const estados = [...ORDEN_ESTADOS.map((estado) => presentarEstado(estado)), presentarEstado(null)]

  return (
    <div className={estilos.pagina}>
      <header className={estilos.cabecera}>
        <p className={estilos.marca}>
          AguaVigía <span>Cartagena</span>
        </p>
        <fieldset className={estilos.tema}>
          <legend className={estilos.oculto}>Tema</legend>
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
      </header>

      <main id="contenido" tabIndex={-1} className={estilos.contenido}>
        <div className={estilos.encabezado}>
          <p className={estilos.rotulo}>Muestrario provisional · F1</p>
          <h1>Identidad del frontend</h1>
          <p className={estilos.bajada}>
            Tokens de <code>DESIGN.md</code> en los dos temas, con el contraste medido en esta pantalla. El mapa
            reemplaza esta página en F2.
          </p>
        </div>

        <section aria-labelledby="titulo-estados" className={estilos.bloque}>
          <h2 id="titulo-estados">Estados del servicio</h2>
          <ul className={estilos.lista}>
            {estados.map((estado) => (
              <li key={estado.clave} className={estilos.fila}>
                <span
                  aria-hidden="true"
                  className={estado.variableColor ? estilos.muestra : `${estilos.muestra} ${estilos.trama}`}
                  style={estado.variableColor ? { background: `var(${estado.variableColor})` } : undefined}
                />
                <span className={estilos.nombreEstado}>{estado.texto}</span>
                <code className={estilos.token}>{estado.variableColor ?? 'trama'}</code>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="titulo-accion" className={estilos.bloque}>
          <h2 id="titulo-accion">Acción y combinaciones permitidas</h2>
          <p className={estilos.secundario}>
            Contraste medido{lectura ? ` en el tema ${lectura.tema}` : ''}. El texto normal exige 4,5:1.
          </p>
          <ul className={estilos.lista}>
            {COMBINACIONES_ACCION.map(([fondo, nombre]) => (
              <li key={fondo} className={estilos.combinacion} style={{ background: `var(${fondo})` }}>
                <span className={estilos.textoAccion}>{nombre}</span>
                <span className={estilos.razon}>{razon(lectura, '--cardenillo', fondo)}</span>
              </li>
            ))}
            <li className={estilos.combinacion}>
              <span className={estilos.boton}>Botón principal</span>
              <span className={estilos.razon}>{razon(lectura, '--sobre-cardenillo', '--cardenillo')}</span>
            </li>
            <li className={estilos.combinacion}>
              <span className={estilos.textoLaton}>Latón: foco y selección</span>
              <span className={estilos.razon}>{razon(lectura, '--laton', '--papel')}</span>
            </li>
          </ul>
        </section>

        <section aria-labelledby="titulo-base" className={estilos.bloque}>
          <h2 id="titulo-base">Paleta base</h2>
          <ul className={`${estilos.lista} ${estilos.paleta}`}>
            {PALETA_BASE.map(([variable, nombre]) => (
              <li key={variable} className={estilos.fila}>
                <span aria-hidden="true" className={estilos.muestra} style={{ background: `var(${variable})` }} />
                <span>{nombre}</span>
                <code className={estilos.token}>{variable}</code>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="titulo-tipografia" className={estilos.bloque}>
          <h2 id="titulo-tipografia">Tipografía</h2>
          <p className={estilos.secundario}>Texto de muestra, no describe ningún barrio real.</p>
          <dl className={estilos.tipos}>
            <div>
              <dt>Barrio · Newsreader</dt>
              <dd className={estilos.barrio}>Nombre del barrio</dd>
            </div>
            <div>
              <dt>Cifra · sistema</dt>
              <dd className={`${estilos.cifra} cifra`}>6:10 p. m.</dd>
            </div>
            <div>
              <dt>Cuerpo · sistema</dt>
              <dd>Texto corrido de lectura: así se ve un párrafo de ayuda en el cuerpo de la página.</dd>
            </div>
            <div>
              <dt>Rótulo · sistema</dt>
              <dd className={estilos.rotulo}>Inicio anunciado</dd>
            </div>
          </dl>
        </section>
      </main>
    </div>
  )
}
