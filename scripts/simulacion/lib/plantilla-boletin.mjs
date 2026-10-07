// Boletines de la simulación, escritos con la plantilla de los reales de Acuacar (ver backend/src/main/resources/ingesta-local/): «Aguas de
// Cartagena informa a la comunidad que este jueves 16 de julio, entre las 8:00 a. m. y las 8:00 p. m., ejecutará… En los siguientes barrios y
// sectores: A, B, C.» Llevan el formato de la API de WordPress (id, fecha, enlace, titulo, contenido en HTML, portada) y se declaran simulados en
// el título, en el enlace (simulacion.local) y en el cuerpo: nadie debe confundirlos con un aviso real.

import { hora12 } from './guion.mjs';

const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** «2026-11-02» → «lunes 2 de noviembre». */
export function fechaLarga(dia) {
  const [anio, mes, d] = dia.split('-').map(Number);
  const fecha = new Date(Date.UTC(anio, mes - 1, d));
  return `${DIAS[fecha.getUTCDay()]} ${d} de ${MESES[mes - 1]}`;
}

function fechaConAnio(dia) {
  const [anio, mes, d] = dia.split('-').map(Number);
  return `${d} de ${MESES[mes - 1]} de ${anio}`;
}

const escapar = (texto) => texto.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');

function boletin({ numero, publicadoEn, titulo, parrafos }) {
  return {
    id: numero,
    fecha: publicadoEn,
    enlace: `https://simulacion.local/boletin/${numero}`,
    titulo: `#${2900 + numero} – [SIMULACIÓN] ${titulo}`,
    contenido: `<div class="gspb_text">${parrafos.join('<br><br>')}</div>`,
    portada: null,
  };
}

// Empieza por «Aguas de Cartagena» a propósito: el extractor corta la enumeración de barrios donde vuelve la prosa de la empresa, y si el aviso
// arrancara con otra cosa su primera frase entraría como un barrio más (probado en PlantillasDeSimulacionTest).
const AVISO_SIMULADO = 'Aguas de Cartagena no publicó este boletín: es una SIMULACIÓN generada por el sistema para ensayar AguaVigía.';

/** Un corte programado con ventana y enumeración de barrios: el caso que Acuacar publica solo (confianza alta). */
export function boletinDeCorte({ numero, dia, publicadoEn, desde, hasta, barrios }) {
  if (!barrios || barrios.length === 0) throw new Error('Un boletín de corte necesita al menos un barrio');
  return boletin({
    numero,
    publicadoEn,
    titulo: 'AGUAS DE CARTAGENA SUSPENDERÁ EL SERVICIO DE ACUEDUCTO EN VARIOS BARRIOS',
    parrafos: [
      `<strong>Cartagena de Indias, ${fechaConAnio(dia)}.</strong> Aguas de Cartagena informa a la comunidad que este ${fechaLarga(dia)}, `
        + `entre las ${hora12(desde)} y las ${hora12(hasta)}, ejecutará trabajos programados en la red de acueducto.`,
      'Durante la ejecución de estos trabajos se presentará suspensión del servicio de acueducto en los siguientes barrios y sectores:',
      `${barrios.map(escapar).join(', ')}.`,
      AVISO_SIMULADO,
    ],
  });
}

/** Una mención suelta en prosa, sin ventana ni enumeración: el extractor la lee con confianza baja y no sale sola al mapa. */
export function boletinDeBajaConfianza({ numero, publicadoEn, barrio }) {
  return boletin({
    numero,
    publicadoEn,
    titulo: 'AGUAS DE CARTAGENA REALIZA LABORES DE MANTENIMIENTO EN LA CIUDAD',
    parrafos: [
      'Aguas de Cartagena adelanta labores de mantenimiento preventivo en distintos puntos de la ciudad, y podría presentarse una '
        + `suspensión temporal del servicio de acueducto en el barrio ${escapar(barrio)} por trabajos de la empresa.`,
      AVISO_SIMULADO,
    ],
  });
}

/** Un aviso de aplazamiento: no anuncia un corte nuevo, dice que uno anunciado se mueve. */
export function boletinDeAplazamiento({ numero, dia, publicadoEn, barrio }) {
  return boletin({
    numero,
    publicadoEn,
    titulo: 'AGUAS DE CARTAGENA INFORMA QUE SE APLAZA LA SUSPENSIÓN DEL SERVICIO DE ACUEDUCTO',
    parrafos: [
      `Aguas de Cartagena informa a la comunidad que se aplaza la suspensión del servicio de acueducto programada para el ${fechaLarga(dia)} `
        + `en el barrio ${escapar(barrio)}. Una nueva fecha será anunciada oportunamente.`,
      AVISO_SIMULADO,
    ],
  });
}
