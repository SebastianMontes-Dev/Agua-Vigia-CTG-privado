import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import type { ProxyOptions } from 'vite'

const backend = process.env.AGUAVIGIA_BACKEND ?? 'http://localhost:8081'

// El proxy hace que la SPA y la API compartan origen en desarrollo, igual que detrás de nginx.
const proxy: Record<string, ProxyOptions> = Object.fromEntries(
  ['/api', '/fotos'].map((ruta) => [ruta, { target: backend, changeOrigin: true }]),
)
// Spring no sirve portadas: este bloque reproduce el proxy acotado de infra/nginx/nginx.conf.
proxy['/acuacar-media'] = {
  target: 'https://www.acuacar.com',
  changeOrigin: true,
  rewrite: (ruta: string) => ruta.replace(/^\/acuacar-media\//, '/wp-content/uploads/'),
  configure: (servidor: import('vite').HttpProxy.ProxyServer) => {
    servidor.on('proxyReq', (peticion) => {
      peticion.removeHeader('referer')
      peticion.setHeader('User-Agent', 'AguaVigiaCTG-Bot/1.0 (+alertas@aguavigia.com)')
    })
  },
}

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true, proxy },
  preview: { port: 4173, strictPort: true, proxy },
  build: { target: 'es2022', sourcemap: true },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['src/pruebas/preparacion.ts'],
    css: { modules: { classNameStrategy: 'non-scoped' } },
  },
})
