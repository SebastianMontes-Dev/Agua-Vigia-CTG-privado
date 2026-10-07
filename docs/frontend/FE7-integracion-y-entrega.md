# FE7 · Integración y entrega

**Objetivo:** cerrar el frontend para la sustentación. Cubrir todo el contrato, medir accesibilidad y rendimiento, retirar
lo provisional y dejar un guion de demostración que funcione con un solo `docker compose up`.

Rama: `feat/fe7-integracion`. Depende de FE0–FE6. Esfuerzo: 2 sesiones.

## Trabajo

1. **Cobertura del contrato.** Un script (`frontend/scripts/cobertura-contrato.mjs`) lista las rutas de `backend/openapi.yaml` que ningún módulo de `src/api` llama.
   - Solo pueden quedar las excluidas en el [README](README.md#fases): `/api/sim/**`, `/api/iot/presion`, `/api/v2/requests.json` y `/api/cuentas/enlaces/*`, más las JSON de FE6b si no se hizo.
   - Se añade al CI.
2. **Lo provisional:**
   - `/muestrario` se retira de la navegación o queda solo en desarrollo
   - `Pendiente.tsx` desaparece: `/historial` lleva a las tres páginas de la historia pública o se elimina
3. **Accesibilidad** ([`guia-frontend.md` §8.2](../diseno/guia-frontend.md#82-comprobaciones-posteriores-sobre-la-interfaz)):
   - axe sin violaciones en todas las pantallas, con `@axe-core/playwright` en e2e
   - todo recorrible con teclado y foco visible
   - el mapa tiene alternativa en lista
   - texto al 200 %
   - movimiento reducido
4. **Rendimiento:**
   - con 3G simulado, una respuesta útil antes de 3 s (RNF001)
   - con 211 sectores, un solo SSE por pestaña
   - la geometría se cachea un día
   - se anotan las cifras reales en este archivo
5. **Cinco segundos:** la prueba con una persona en las pantallas principales (mapa, ficha, cumplimiento, panel), anotando el resultado.
6. **Guion de la demo** en `docs/frontend/guion-demo.md`. Pasos exactos, desde un `docker compose up` limpio:
   - ciudadano reporta con foto
   - el veedor modera
   - el consenso cambia el mapa en vivo
   - el corte se cierra
   - el Índice se mueve
   - un suscriptor recibe el aviso y responde «ya volvió»

   Con alternativa en modo simulación, con su banner. Es la base de la guía «08 · Guion de la presentación» de `docs/README.md`.
7. **Documentación:**
   - `guia-frontend.md` §8.2 se marca con los resultados reales
   - el README de esta carpeta, con todas las fases ✅
   - `docs/api/cambios-para-frontend.md`: se anota qué secciones F0–F6 quedaron adoptadas

## Terminado cuando

- Pasa la puerta completa.
- La cobertura del contrato no tiene huecos fuera de las exclusiones.
- axe está en verde.
- El guion de la demo se ejecutó de principio a fin sobre una base limpia sin tocar nada a mano.

## Prompt para Claude Code

```
Usa la skill disenar-frontend. Lee docs/frontend/README.md y docs/frontend/FE7-integracion-y-entrega.md.
En feat/fe7-integracion desde main: crea el script de cobertura del contrato y súmalo al CI, retira lo provisional,
añade axe a los e2e, mide rendimiento con 3G simulado, escribe docs/frontend/guion-demo.md y ejecútalo de principio a fin
sobre un docker compose up limpio. Corre la puerta completa y muéstrame el resultado. Abre el PR, no lo fusiones.
Si usas subagentes, usa model sonnet.
```
