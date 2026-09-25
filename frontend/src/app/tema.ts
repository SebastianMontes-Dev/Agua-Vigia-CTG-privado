export type PreferenciaTema = 'sistema' | 'claro' | 'oscuro'

const CLAVE = 'aguavigia.tema'
const ATRIBUTO: Record<PreferenciaTema, 'light' | 'dark' | null> = {
  sistema: null,
  claro: 'light',
  oscuro: 'dark',
}

export function leerPreferenciaTema(): PreferenciaTema {
  try {
    const guardada = window.localStorage.getItem(CLAVE)
    return guardada === 'claro' || guardada === 'oscuro' ? guardada : 'sistema'
  } catch {
    return 'sistema'
  }
}

export function aplicarTema(preferencia: PreferenciaTema): void {
  const atributo = ATRIBUTO[preferencia]
  if (atributo) document.documentElement.dataset.theme = atributo
  else delete document.documentElement.dataset.theme

  try {
    if (preferencia === 'sistema') window.localStorage.removeItem(CLAVE)
    else window.localStorage.setItem(CLAVE, preferencia)
  } catch {
    // Sin almacenamiento (navegación privada, datos bloqueados) el tema vale solo para esta visita.
  }
}
