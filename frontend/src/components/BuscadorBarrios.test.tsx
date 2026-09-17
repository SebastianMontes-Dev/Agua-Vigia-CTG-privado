import { fireEvent, render, screen } from '@testing-library/react'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import { BuscadorBarrios } from './BuscadorBarrios'
import type { Sector } from '../types/tipos-dominio'

// jsdom no implementa scrollIntoView — el componente lo usa para mantener la fila resaltada
// por teclado visible dentro del desplegable (no hay nada que verificar sobre el scroll aquí).
beforeAll(() => {
  Element.prototype.scrollIntoView = vi.fn()
})

const SECTORES: Sector[] = [
  { id: 'manga', nombre: 'Manga', estado: null, actualizadoEn: null },
  { id: 'bocagrande', nombre: 'Bocagrande', estado: null, actualizadoEn: null },
]

/**
 * Sin esta semántica, un lector de pantalla no anuncia que el campo abre una lista de
 * sugerencias ni cuál fila está resaltada al navegar con flechas — solo lo delataba visualmente
 * la clase `is-seleccionado`.
 */
describe('BuscadorBarrios — semántica de combobox', () => {
  it('el campo declara su rol de combobox y el desplegable que controla', () => {
    render(
      <BuscadorBarrios
        sectores={SECTORES}
        busqueda=""
        onCambiarBusqueda={vi.fn()}
        cargando={false}
        error={null}
      />,
    )

    const campo = screen.getByRole('combobox', { name: /buscar barrio/i })
    expect(campo).toHaveAttribute('aria-autocomplete', 'list')
    expect(campo).toHaveAttribute('aria-expanded', 'false')
    expect(campo).toHaveAttribute('aria-controls', 'buscador-barrios-listbox')
  })

  it('al abrir y navegar con flechas, aria-activedescendant sigue a la fila resaltada', () => {
    render(
      <BuscadorBarrios
        sectores={SECTORES}
        busqueda=""
        onCambiarBusqueda={vi.fn()}
        cargando={false}
        error={null}
      />,
    )

    const campo = screen.getByRole('combobox', { name: /buscar barrio/i })
    fireEvent.focus(campo)
    expect(campo).toHaveAttribute('aria-expanded', 'true')

    const listbox = screen.getByRole('listbox')
    expect(listbox).toHaveAttribute('id', 'buscador-barrios-listbox')
    const opciones = screen.getAllByRole('option')
    expect(opciones).toHaveLength(2)
    expect(campo).not.toHaveAttribute('aria-activedescendant')

    fireEvent.keyDown(campo, { key: 'ArrowDown' })

    expect(campo).toHaveAttribute('aria-activedescendant', opciones[0].id)
    expect(opciones[0]).toHaveAttribute('aria-selected', 'true')
    expect(opciones[1]).toHaveAttribute('aria-selected', 'false')
  })
})
