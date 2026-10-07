// El tiempo de la simulación visto desde el simulador: cuántos minutos simulados han pasado desde el inicio del guion. No sabe nada de la API ni
// del reloj del backend (eso lo hace el ejecutor con POST /api/sim/reloj); solo lleva la cuenta, a la velocidad que toque.
//
// `velocidad` es cuántos minutos simulados pasan por cada minuto real: x60 → un segundo real es un minuto simulado.

function exigirVelocidad(velocidad) {
  if (!Number.isFinite(velocidad) || velocidad <= 0) {
    throw new Error(`La velocidad debe ser un número mayor que 0 (era ${velocidad})`);
  }
}

export class Cronometro {
  #minuto = 0;
  #velocidad;
  #pausado = false;
  #congelado = false;

  constructor({ velocidad = 60 } = {}) {
    exigirVelocidad(velocidad);
    this.#velocidad = velocidad;
  }

  get minuto() {
    return this.#minuto;
  }

  get velocidad() {
    return this.#velocidad;
  }

  get pausado() {
    return this.#pausado;
  }

  /** Pasa `milisegundosReales` de tiempo real: avanza solo si no está pausado ni congelado. */
  avanzar(milisegundosReales) {
    if (this.#pausado || this.#congelado) return;
    this.#minuto += (milisegundosReales / 60_000) * this.#velocidad;
  }

  fijarVelocidad(velocidad) {
    exigirVelocidad(velocidad);
    this.#velocidad = velocidad;
  }

  pausar() {
    this.#pausado = true;
  }

  reanudar() {
    this.#pausado = false;
  }

  /** Detiene el tiempo mientras se comprueban las aserciones de un acto, sin tocar la pausa que haya pedido el usuario. */
  congelar() {
    this.#congelado = true;
  }

  descongelar() {
    this.#congelado = false;
  }

  /** Adelanta `minutos` simulados de golpe (el `saltar <horas>` del simulador). */
  saltar(minutos) {
    if (!Number.isFinite(minutos) || minutos <= 0) {
      throw new Error(`Hay que saltar una cantidad positiva de minutos (era ${minutos})`);
    }
    this.#minuto += minutos;
  }

  estado() {
    return { minuto: this.#minuto, velocidad: this.#velocidad, pausado: this.#pausado };
  }
}
