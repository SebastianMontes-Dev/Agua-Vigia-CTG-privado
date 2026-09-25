import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './estilos/index.css'
import { Muestrario } from './app/Muestrario'
import { aplicarTema, leerPreferenciaTema } from './app/tema'

aplicarTema(leerPreferenciaTema())

const raiz = document.getElementById('raiz')
if (!raiz) throw new Error('Falta el elemento #raiz en index.html')

createRoot(raiz).render(
  <StrictMode>
    <Muestrario />
  </StrictMode>,
)
