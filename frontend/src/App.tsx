/**
 * App — raíz de la SPA de AguaVigía CTG.
 *
 * Ensambla: router, encabezado con selector de tema, rutas y layout.
 * El tema se inicializa aquí y se propaga al DOM vía data-theme en :root
 * (ver useTheme y DESIGN.md §3).
 */
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom'
import { Component, lazy, Suspense, useState } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Encabezado } from './components/Encabezado'
import { useTheme } from './hooks/useTheme'
import { ModalSuscripcion } from './components/ModalSuscripcion'
import { SplashScreen } from './components/SplashScreen'
import { obtenerSectores } from './api/services'

// Cada vista carga solo cuando se visita, especialmente útil en conexiones móviles.
const PaginaMapa = lazy(() => import('./pages/PaginaMapa'))
const PaginaReportar = lazy(() => import('./pages/PaginaReportar'))
const PaginaConfirmarReporte = lazy(() => import('./pages/PaginaConfirmarReporte'))
const PaginaSector = lazy(() => import('./pages/PaginaSector'))
const PaginaVeedor = lazy(() => import('./pages/PaginaVeedor'))
const PaginaCuentas = lazy(() => import('./pages/PaginaCuentas'))
const PaginaRegistroCuenta = lazy(() => import('./pages/PaginaRegistroCuenta'))
const PaginaOlvideClave = lazy(() => import('./pages/PaginaOlvideClave'))
const PaginaVerificarCorreo = lazy(() => import('./pages/PaginaVerificarCorreo'))
const PaginaFijarClave = lazy(() => import('./pages/PaginaFijarClave'))
const PaginaNoEncontrada = lazy(() => import('./pages/PaginaNoEncontrada'))

// Al recargar la página el navegador restaura por su cuenta la posición de scroll que
// tenía antes del reload (aunque la URL sea la misma "/"), así que un reload en la sección
// Estadísticas "aterriza" ahí en vez de en el inicio. Desactivarlo aquí, antes de que React
// monte nada, deja el control del scroll enteramente al efecto de hash en PaginaMapa.
if (typeof window !== 'undefined' && 'scrollRestoration' in window.history) {
  window.history.scrollRestoration = 'manual'
}

interface RouteErrorBoundaryProps { children: ReactNode }
interface RouteErrorBoundaryState { hasError: boolean }

class RouteErrorBoundary extends Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): RouteErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('No se pudo cargar la vista solicitada:', error, info)
  }

  render() {
    if (this.state.hasError) {
      return (
        <main id="contenido-principal" tabIndex={-1} role="alert" className="cargando-pagina">
          <div style={{ maxWidth: '30rem', textAlign: 'center' }}>
            <h1 style={{ fontSize: '1.35rem', marginBottom: '0.75rem' }}>No pudimos cargar esta vista</h1>
            <p style={{ marginBottom: '1rem' }}>Comprueba tu conexión e inténtalo nuevamente.</p>
            <button type="button" className="boton boton-primario" onClick={() => window.location.reload()}>
              Recargar vista
            </button>
          </div>
        </main>
      )
    }

    return this.props.children
  }
}

/**
 * ContenidoApp — vive dentro de BrowserRouter (useLocation lo exige) y decide el shell.
 *
 * "/" (la página principal) usa su propio chrome flotante (ver PaginaMapa +
 * NavegacionFlotante) a pantalla completa, sin el sidebar/topbar de Encabezado, y maneja su
 * propio ModalSuscripcion, pasándole los `sectores` que ya trae de su propio useDatosEnVivo (sin
 * volver a pedirlos — dos instancias de useDatosEnVivo en la misma vista abrirían dos conexiones
 * SSE). El resto de rutas conserva el shell de siempre, con su propia instancia del modal para el
 * botón "Suscribirme" del Encabezado, alimentada por un fetch simple y cacheado (no SSE: estas
 * vistas no necesitan datos en vivo del mapa para nada más).
 */
function ContenidoApp() {
  const { temaActivo, alternarTema } = useTheme()
  const { pathname } = useLocation()
  const esPaginaPrincipal = pathname === '/'
  const [suscripcionAbierta, setSuscripcionAbierta] = useState(false)
  // Sin SSE: este shell (todas las rutas menos "/") no necesita datos en vivo del mapa, solo la
  // lista de sectores para el desplegable del formulario — un fetch simple, cacheado, y solo
  // mientras el modal está abierto, en vez de useDatosEnVivo (que abriría una conexión SSE que
  // esta vista no usa para nada más).
  const sectoresSuscripcion = useQuery({
    queryKey: ['sectores-para-suscripcion'],
    queryFn: () => obtenerSectores().then((r) => r.sectores),
    enabled: suscripcionAbierta,
    staleTime: 60_000,
  })

  return (
    <RouteErrorBoundary key={pathname}>
      <Suspense fallback={
        <main id="contenido-principal" tabIndex={-1} className="cargando-pagina" aria-busy="true">
          <div role="status"><span /> Cargando experiencia…</div>
        </main>
      }>
        {esPaginaPrincipal ? (
          <PaginaMapa temaActivo={temaActivo} onAlternarTema={alternarTema} />
        ) : (
          <>
            <Encabezado temaActivo={temaActivo} onAlternarTema={alternarTema} onAbrirSuscripcion={() => setSuscripcionAbierta(true)} />
            <div className="app-main">
              <Routes>
                <Route path="/reportar" element={<PaginaReportar />} />
                <Route path="/confirmar/:id" element={<PaginaConfirmarReporte />} />
                <Route path="/sectores/:id" element={<PaginaSector />} />
                <Route path="/veedor" element={<PaginaVeedor />} />
                <Route path="/veedor/cuentas" element={<PaginaCuentas />} />
                {/* Las rutas /cuentas/* son las que aterrizan desde los enlaces del correo: el
                    token viaja como query param y cada pantalla lo canjea contra la API. */}
                <Route path="/cuentas/registro" element={<PaginaRegistroCuenta />} />
                <Route path="/cuentas/olvide-mi-clave" element={<PaginaOlvideClave />} />
                <Route path="/cuentas/verificar" element={<PaginaVerificarCorreo />} />
                <Route path="/cuentas/invitacion" element={<PaginaFijarClave modo="invitacion" />} />
                <Route path="/cuentas/restablecer" element={<PaginaFijarClave modo="restablecimiento" />} />
                <Route path="*" element={<PaginaNoEncontrada />} />
              </Routes>
            </div>
            <ModalSuscripcion
              abierto={suscripcionAbierta}
              onCerrar={() => setSuscripcionAbierta(false)}
              sectores={sectoresSuscripcion.data ?? []}
            />
          </>
        )}
      </Suspense>
    </RouteErrorBoundary>
  )
}

function App() {
  return (
    <BrowserRouter>
      <SplashScreen />
      <a
        href="#contenido-principal"
        id="saltar-al-contenido"
        style={{
          position: 'absolute',
          top: '-999px',
          left: '-999px',
          zIndex: 9999,
          padding: '0.5rem 1rem',
          backgroundColor: 'var(--color-acento)',
          color: '#fff',
          borderRadius: 'var(--radio-base)',
          fontFamily: 'var(--font-cuerpo)',
          textDecoration: 'none',
        }}
        onFocus={(e) => {
          const el = e.currentTarget as HTMLAnchorElement
          el.style.top = '0.5rem'
          el.style.left = '0.5rem'
        }}
        onBlur={(e) => {
          const el = e.currentTarget as HTMLAnchorElement
          el.style.top = '-999px'
          el.style.left = '-999px'
        }}
      >
        Ir al contenido principal
      </a>

      <div className="app-shell">
        <ContenidoApp />
      </div>
    </BrowserRouter>
  )
}

export default App
