import estilos from './MarcaRecibido.module.css'

export function MarcaRecibido() {
  return (
    <svg className={estilos.marca} viewBox="0 0 48 48" aria-hidden="true">
      <circle cx="24" cy="24" r="21" />
      <path d="M14.5 24.5l6.5 6.5 13-14" />
    </svg>
  )
}
