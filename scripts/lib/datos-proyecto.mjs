/**
 * Fuente única de los datos reales del proyecto: los registros en markdown de
 * docs/ (ADRs, bugs, recomendaciones, sprints, cobertura). La usa
 * scripts/generar-dashboard.mjs para la Sala de control — un dato, un lugar
 * (protocolo-de-contexto.md §2). El generador no vuelve a leer ni a calcular
 * nada por su cuenta.
 *
 * No inventa nada que no esté en el repositorio. Si un archivo cambia de
 * formato, este módulo debe fallar de forma visible (no rellenar en silencio).
 */
import { execFileSync } from "node:child_process";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RAIZ = path.resolve(__dirname, "..", "..");

function leer(rel) {
  // Normaliza CRLF -> LF: varios archivos del repo se versionan con CRLF en Windows,
  // y las regex de este modulo asumen \n como separador de linea.
  return readFileSync(path.join(RAIZ, rel), "utf8").replace(/\r\n/g, "\n");
}

/** Divide una fila de tabla markdown en columnas, respetando `\|` escapado dentro de backticks. */
function columnasDeFila(fila) {
  const marcador = " PIPE ";
  return fila
    .replace(/\\\|/g, marcador)
    .split("|")
    .map((c) => c.replace(new RegExp(marcador, "g"), "|").trim());
}

// ─── ADRs: docs/design-decisions.md ───
function obtenerADRs() {
  const texto = leer("docs/design-decisions.md");
  const adrs = [];
  const re = /^## ADR-(\d+) — (.+)$/gm;
  let m;
  while ((m = re.exec(texto))) {
    const bloque = texto.slice(m.index, m.index + 600);
    const estadoMatch = bloque.match(/- \*\*Estado:\*\* (.+)/);
    const fechaMatch = bloque.match(/- \*\*Fecha:\*\* (\d{4}-\d{2}-\d{2})/);
    const estadoTexto = estadoMatch ? estadoMatch[1] : "";
    const pendiente = /propuesta|pendiente/i.test(estadoTexto);
    adrs.push({ numero: Number(m[1]), titulo: m[2].trim(), pendiente, fecha: fechaMatch ? fechaMatch[1] : null });
  }
  return adrs;
}

// ─── Bugs: docs/gestion/registro-de-bugs.md, tabla de estado ───
// La columna Estado a veces trae solo la palabra (`Abierto`, `Cerrado`) y a veces una nota larga
// (ej. BUG-029: "🟡 Parcial — ítems 1, 2, 4 y 5 cerrados; ítem 3 ... sigue abierto"). Se separa en un
// balde corto para mostrar en un badge, y el texto largo aparte.
function normalizarEstadoBug(estadoRaw) {
  const texto = estadoRaw.trim();
  let estado = "Abierto";
  if (/^cerrado/i.test(texto)) estado = "Cerrado";
  else if (/^en curso/i.test(texto)) estado = "En curso";
  else if (/^no se corrige/i.test(texto)) estado = "No se corrige";
  else if (/parcial/i.test(texto)) estado = "Parcial";
  const notaMatch = texto.match(/—\s*(.*)$/);
  const notaEstado = notaMatch ? notaMatch[1].trim() : (texto === estado ? null : texto.replace(/^[🟡🟢🔴]\s*/, ""));
  return { estado, notaEstado };
}

function obtenerBugs() {
  const texto = leer("docs/gestion/registro-de-bugs.md");
  const tabla = texto.match(/\| ID \| Fecha \| Sev \|.*?\n\|---.*?\n([\s\S]*?)\n\n/);
  if (!tabla) return [];
  const filas = tabla[1].trim().split("\n").filter((l) => l.startsWith("| BUG-"));
  return filas.map((f) => {
    const cols = columnasDeFila(f).filter((_, i, arr) => i > 0 && i < arr.length - 1);
    const [id, fecha, sev, modulo, titulo, estadoRaw] = cols;
    const { estado, notaEstado } = normalizarEstadoBug(estadoRaw);
    return { id, fecha, sev, modulo, titulo, estado, notaEstado };
  });
}

// ─── Recomendaciones de la IA: docs/gestion/recomendaciones-ia.md ───
function obtenerRecomendaciones() {
  const texto = leer("docs/gestion/recomendaciones-ia.md");
  const tabla = texto.match(/\| ID \| Fecha \| Título \|.*?\n\|---.*?\n([\s\S]*?)\n\n/);
  if (!tabla) return [];
  const filas = tabla[1].trim().split("\n").filter((l) => l.startsWith("| REC-"));
  return filas.map((f) => {
    const cols = columnasDeFila(f).filter((_, i, arr) => i > 0 && i < arr.length - 1);
    const [id, fecha, titulo, estado] = cols;
    const bloque = texto.match(new RegExp(`### ${id} — .*?\\n\\n- \\*\\*Fecha:\\*\\*[^\\n]*\\n\\n([\\s\\S]*?)(?=\\n### |\\n---|$)`));
    return { id, fecha, titulo, estado, detalle: bloque ? bloque[1].trim() : "" };
  });
}

// ─── Detalle de un sprint: docs/gestion/sprint-N.md (si existe) ───
// La tabla de "Compromisos" de cada sprint.md ya trae su propio estado (✅/🟡/nada) puesto a mano al
// cerrar cada entregable — no se infiere de otra fuente, se lee tal cual está escrito.
function leerDetalleSprint(n) {
  const ruta = path.join(RAIZ, "docs/gestion", `sprint-${n}.md`);
  if (!existsSync(ruta)) return null;
  const texto = leer(`docs/gestion/sprint-${n}.md`);
  const abiertoM = texto.match(/\*\*Abierto:\*\*\s*(\d{4}-\d{2}-\d{2})/);
  const cerradoM = texto.match(/\*\*Cerrado:\*\*\s*(\d{4}-\d{2}-\d{2})/);
  // "Un sprint no cierra por calendario: cierra cuando su entregable se demuestra funcionando"
  // (CLAUDE.md). Ese criterio esta escrito entre parentesis al lado de "Cerrado: —".
  const criterioM = texto.match(/\*\*Cerrado:\*\*\s*—\s*\*\(([\s\S]*?)\)\*/);
  const objetivoM = texto.match(/## 1\. Objetivo del sprint\s*\n+\*\*([\s\S]*?)\*\*/);
  const limpiarParrafo = (s) => s.replace(/\s*\n\s*/g, " ").trim();
  const tabla = texto.match(/\| RF\/RNF \| Entregable \|.*?\n\|---.*?\n([\s\S]*?)\n\n/);
  const compromisos = tabla
    ? tabla[1].trim().split("\n").filter((l) => l.startsWith("|")).map((f) => {
        const cols = columnasDeFila(f).filter((_, i, arr) => i > 0 && i < arr.length - 1);
        const [rf, entregableRaw, dependeDe] = cols;
        // El estado se lee de dos formas: una columna `Estado` al final (4 columnas) o el mismo
        // `✅`/`🟡` al principio del Entregable, que es como se ha venido marcando desde sprint-1.md.
        // Si se soportara solo la columna, los entregables ya entregados seguirian contando como
        // pendientes y el avance saldria mas bajo de lo real.
        const marcaEnEntregable = (entregableRaw || "").match(/^(✅|🟡)\s*([^—\-]*)[—\-]?\s*/u);
        const estadoRaw = cols[3] || "";
        const marca = estadoRaw.match(/^(✅|🟡)/u) ? estadoRaw[0] : (marcaEnEntregable ? marcaEnEntregable[1] : "");
        const estado = marca === "✅" ? "hecho" : marca === "🟡" ? "parcial" : "pendiente";
        const nota = estadoRaw
          ? estadoRaw.replace(/^(✅|🟡)\s*/u, "")
          : (marcaEnEntregable ? marcaEnEntregable[2].trim() : "");
        const entregable = marcaEnEntregable ? entregableRaw.slice(marcaEnEntregable[0].length) : entregableRaw;
        return { rf, entregable, dependeDe, estado, nota };
      })
    : [];
  return {
    abierto: abiertoM ? abiertoM[1] : null,
    cerrado: cerradoM ? cerradoM[1] : null,
    objetivo: objetivoM ? limpiarParrafo(objetivoM[1]) : null,
    criterioCierre: criterioM ? limpiarParrafo(criterioM[1]) : null,
    compromisos
  };
}

// ─── Sprints: docs/gestion/README.md, tabla "Los siete sprints" ───
function obtenerSprints() {
  const texto = leer("docs/gestion/README.md");
  const tabla = texto.match(/\| Sprint \| Foco \| Entregable que lo cierra \|.*?\n\|---.*?\n([\s\S]*?)\n\n/);
  if (!tabla) throw new Error("No se pudo parsear la tabla de sprints — revisar formato de docs/gestion/README.md, sección \"Los siete sprints\"");
  const limpiar = (s) => s.replace(/\*\*/g, "").replace(/`/g, "").trim();
  const filas = tabla[1].trim().split("\n").filter((l) => l.startsWith("|"));
  const sprints = filas.map((f) => {
    const cols = columnasDeFila(f).filter((_, i, arr) => i > 0 && i < arr.length - 1);
    const [sprintRaw, foco, entregable] = cols;
    const n = Number((sprintRaw.match(/\d+/) || [0])[0]);
    return { n, enfoque: limpiar(foco), entregable: limpiar(entregable) };
  });
  // El sprint activo es el de mayor numero que ya tiene su sprint-N.md (documento de seguimiento).
  let activo = 0;
  for (const s of sprints) {
    if (existsSync(path.join(RAIZ, "docs/gestion", `sprint-${s.n}.md`))) activo = s.n;
  }
  sprints.forEach((s) => { s.activo = s.n === activo; s.detalle = leerDetalleSprint(s.n); });
  return sprints;
}

// ─── Fecha de inicio del trabajo: apertura del primer sprint, no el primer commit ───
// El historial de git se reinició (chore: iniciar historial privado), así que el primer commit no
// dice cuándo empezó el proyecto; el "Abierto" del sprint más antiguo sí.
function obtenerFechaInicio(sprints) {
  const abiertos = sprints.map((s) => s.detalle && s.detalle.abierto).filter(Boolean).sort();
  return abiertos[0] || null;
}

// ─── Avance del proyecto: sprints cerrados + fraccion del sprint activo y proyeccion ───
// No hay fecha de entrega ni duracion fija de sprint (docs/gestion/README.md lo dice explicitamente):
// por eso la fecha estimada de cierre solo se calcula cuando ya cerro al menos un sprint de verdad —
// antes de eso seria inventar una cifra a partir de cero datos reales, y este proyecto no hace eso.
function calcularAvanceProyecto(sprints, fechaInicio) {
  const total = sprints.length;
  const cerrados = sprints.filter((s) => s.detalle && s.detalle.cerrado).length;

  const activo = sprints.find((s) => s.activo);
  let fraccionActivo = 0;
  if (activo && activo.detalle && activo.detalle.compromisos.length) {
    const puntos = activo.detalle.compromisos.reduce(
      (acc, c) => acc + (c.estado === "hecho" ? 1 : c.estado === "parcial" ? 0.5 : 0), 0
    );
    fraccionActivo = puntos / activo.detalle.compromisos.length;
  }
  const avanceFraccion = total ? (cerrados + fraccionActivo) / total : 0;

  const ahora = new Date();
  const inicio = fechaInicio ? new Date(`${fechaInicio}T12:00:00`) : null;
  const diasTranscurridos = inicio ? Math.max(1, Math.round((ahora - inicio) / 86400000)) : null;

  const sprintsConCierre = sprints.filter((s) => s.detalle && s.detalle.abierto && s.detalle.cerrado);
  let fechaEstimada = null;
  let diasPromedioPorSprint = null;
  if (sprintsConCierre.length) {
    const duraciones = sprintsConCierre.map((s) => (new Date(s.detalle.cerrado) - new Date(s.detalle.abierto)) / 86400000);
    diasPromedioPorSprint = duraciones.reduce((a, b) => a + b, 0) / duraciones.length;
    const sprintsRestantes = Math.max(0, total - cerrados - fraccionActivo);
    const diasRestantes = Math.round(sprintsRestantes * diasPromedioPorSprint);
    fechaEstimada = new Date(ahora.getTime() + diasRestantes * 86400000).toISOString().slice(0, 10);
  }

  return {
    sprintsTotal: total,
    sprintsCerrados: cerrados,
    porcentaje: Math.round(avanceFraccion * 1000) / 10,
    diasTranscurridos,
    diasPromedioPorSprint: diasPromedioPorSprint !== null ? Math.round(diasPromedioPorSprint * 10) / 10 : null,
    fechaEstimada,
    sprintActivoNum: activo ? activo.n : null,
    sprintActivoAbiertoEn: activo && activo.detalle ? activo.detalle.abierto : null
  };
}

// ─── Cobertura de requisitos: docs/gestion/registro-de-implementaciones.md ───
function obtenerCobertura() {
  const texto = leer("docs/gestion/registro-de-implementaciones.md");
  const totalFunc = texto.match(/\*\*Total funcionales\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)%\*\*/);
  const noFunc = texto.match(/\*\*No funcionales\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)%\*\*/);

  // Desglose por modulo: el total solo dice "vamos en X%"; el desglose dice de que modulo se trata.
  const tabla = texto.match(/\| Módulo \| Requisitos \| Implementados \| % \|\n\|---.*?\n([\s\S]*?)\n\n/);
  const porModulo = tabla
    ? tabla[1].trim().split("\n")
        .filter((l) => /^\|\s*M\d/.test(l))
        .map((f) => {
          const cols = columnasDeFila(f).filter((_, i, arr) => i > 0 && i < arr.length - 1);
          const [modulo, requisitos, implementados] = cols;
          const total = Number(requisitos) || 0;
          const hechos = parseInt(implementados, 10) || 0;
          return {
            modulo: modulo.replace(/\*\*/g, "").trim(),
            estrella: modulo.includes("⭐"),
            total,
            implementados: hechos,
            porcentaje: total ? Math.round((hechos / total) * 100) : 0
          };
        })
    : [];

  return {
    funcionales: totalFunc ? { total: Number(totalFunc[1]), implementados: Number(totalFunc[2]) } : { total: 0, implementados: 0 },
    noFuncionales: noFunc ? { total: Number(noFunc[1]), implementados: Number(noFunc[2]) } : { total: 0, implementados: 0 },
    porModulo
  };
}

// ─── Commits: cuántos y cuándo fue el último (git log, sin depender de un remoto) ───
function obtenerActividadGit() {
  const salida = execFileSync("git", ["log", "--format=%aI"], { cwd: RAIZ, encoding: "utf8" }).trim();
  const fechas = salida ? salida.split("\n") : [];
  return { commits: fechas.length, ultimoCommit: fechas[0] || null };
}

// ─── Ensamblar ───
export function generarDatos() {
  const sprints = obtenerSprints();
  return {
    generadoEn: new Date().toISOString(),
    adrs: obtenerADRs(),
    bugs: obtenerBugs(),
    recomendaciones: obtenerRecomendaciones(),
    sprints,
    cobertura: obtenerCobertura(),
    avanceProyecto: calcularAvanceProyecto(sprints, obtenerFechaInicio(sprints)),
    git: obtenerActividadGit()
  };
}
