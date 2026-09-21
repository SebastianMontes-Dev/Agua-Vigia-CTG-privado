---
name: Historia de usuario
about: Una unidad de valor para un usuario real, trazada a su requisito
title: "[HU0NN] "
labels: historia
---

**Requisito:** RF0NN · **Módulo:** M<N> · **Sprint:** N

## Historia

**Como** <vecino / comerciante / veedor / periodista / sistema>
**quiero** <qué>
**para** <qué obtengo con eso>

<!-- El "para" es el que importa: si no sabes completarlo, la historia probablemente no vale. -->

## Criterios de aceptación (Gherkin)

```gherkin
Escenario: <nombre del escenario>
  Dado <estado inicial>
  Cuando <acción>
  Entonces <resultado observable>

Escenario: <caso de borde — siempre al menos uno>
  Dado
  Cuando
  Entonces
```

## Dependencias

<!-- ¿Necesitas el contrato OpenAPI o el GeoJSON de barrios? Dilo aquí. -->

## Terminado cuando

- [ ] Los escenarios Gherkin pasan como pruebas automatizadas
- [ ] PR fusionado
- [ ] Fila en `docs/gestion/registro-de-implementaciones.md`
- [ ] Fila actualizada en `docs/ingenieria/matriz-trazabilidad.md`
