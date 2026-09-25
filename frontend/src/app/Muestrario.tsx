import { useState } from 'react'
import { ORDEN_ESTADOS, presentarEstado } from '../dominio/estados'
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

export function Muestrario() {
  const [tema, setTema] = useState<PreferenciaTema>(leerPreferenciaTema)

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
        <p className={estilos.display}>Bocagrande · Sin agua desde las 6:10 a. m.</p>
        <p>Prometieron volver a las 2:00 p. m. Tu reporte cuenta junto con el de tus vecinos.</p>
        <p className={estilos.secundario}>
          actualizado hace <time dateTime="PT4M">4 min</time> · <span className="mono">RF001</span>
        </p>
      </section>
    </main>
  )
}
