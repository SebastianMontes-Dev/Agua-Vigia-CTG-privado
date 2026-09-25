// El CSS de Vite acorta `#ffffff` a `#fff`, y así lo devuelve getComputedStyle: hay que aceptar las dos formas.
function aSeisDigitos(hex: string): string {
  return /^#[0-9a-f]{3}$/i.test(hex) ? `#${[...hex.slice(1)].map((d) => d + d).join('')}` : hex
}

function luminancia(hex: string): number {
  const completo = aSeisDigitos(hex)
  const [r, g, b] = [1, 3, 5].map((i) => {
    const canal = parseInt(completo.slice(i, i + 2), 16) / 255
    return canal <= 0.04045 ? canal / 12.92 : ((canal + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0)
}

/** Razón de contraste WCAG entre dos colores `#rrggbb` o `#rgb`, sin redondear. */
export function contraste(a: string, b: string): number {
  const [claro, oscuro] = [luminancia(a), luminancia(b)].sort((x, y) => y - x)
  return ((claro ?? 0) + 0.05) / ((oscuro ?? 0) + 0.05)
}
