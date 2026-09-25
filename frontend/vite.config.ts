import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

const backend = process.env.AGUAVIGIA_BACKEND ?? 'http://localhost:8081'

// El proxy hace que la SPA y la API compartan origen en desarrollo, igual que detrás de nginx.
const proxy = Object.fromEntries(
  ['/api', '/fotos', '/acuacar-media'].map((ruta) => [ruta, { target: backend, changeOrigin: true }]),
)

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
