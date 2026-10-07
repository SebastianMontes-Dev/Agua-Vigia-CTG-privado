// Convierte los actos del guion en una lista plana ordenada por minuto simulado: lo que el ejecutor recorre con el cronómetro. Cada acto aporta sus
// acciones (la acción `reportes` se expande en un `reporte-individual` por identidad) y, al final de su duración, un «fin-de-acto» que lleva las
// aserciones del acto. Sin red ni reloj, para poder probarla.

import { planificarReportes } from './planes.mjs';

export function construirLineaDeTiempo(guion) {
  const elementos = [];
  for (const acto of guion.actos) {
    for (const accion of acto.acciones) {
      if (accion.tipo === 'reportes') {
        for (const { minuto, accion: individual } of planificarReportes(accion, acto)) {
          elementos.push({ minuto, tipo: 'accion', acto: acto.id, accion: individual });
        }
      } else {
        elementos.push({ minuto: acto.minuto, tipo: 'accion', acto: acto.id, accion });
      }
    }
    elementos.push({ minuto: acto.minuto + acto.duracion, tipo: 'fin-de-acto', acto: acto.id, aserciones: acto.aserciones });
  }
  // Array.prototype.sort es estable: a igual minuto se conserva el orden del guion (acciones del acto antes que su fin).
  return elementos.sort((a, b) => a.minuto - b.minuto);
}
