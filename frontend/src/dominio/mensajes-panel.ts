import type { ResultadoAlta, ResultadoConfirmacionFactor, ResultadoIngreso } from '../api/panel'

const SIN_RED = 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo otra vez.'
const FALLO = 'Algo falló de nuestro lado. Inténtalo otra vez en un momento.'

/** cuentas-y-sesion.md, «Estados de una cuenta»: por qué no puede entrar y qué le queda por hacer. */
const CUENTA_NO_HABILITADA: Record<string, string> = {
  PENDIENTE_VERIFICACION: 'Falta confirmar tu correo. Abre el enlace que te enviamos al pedir la cuenta.',
  PENDIENTE_APROBACION: 'Tu correo ya está confirmado. Falta que un administrador apruebe la cuenta.',
  INVITADA: 'Falta aceptar la invitación: abre el enlace del correo y elige tu clave.',
  SUSPENDIDA: 'Tu cuenta está suspendida. Habla con un administrador del panel.',
  RECHAZADA: 'La solicitud de esta cuenta fue rechazada.',
}

export function minutos(segundos: number): string {
  const total = Math.max(1, Math.ceil(segundos / 60))
  return total === 1 ? '1 minuto' : `${total} minutos`
}

function esperar(segundos: number | null): string {
  return segundos
    ? `Hiciste demasiados intentos. Podrás volver a intentarlo en ${minutos(segundos)}.`
    : 'Hiciste demasiados intentos. Espera unos minutos antes de volver a intentarlo.'
}

export function mensajeIngreso(resultado: ResultadoIngreso, conCodigo: boolean): string | null {
  switch (resultado.tipo) {
    case 'ingresado':
    case 'pide-codigo':
      return null
    // Con el código en pantalla la clave ya se aceptó: lo que no coincide es el código.
    case 'credencial-invalida':
      return conCodigo
        ? 'El código no coincide o ya se usó. Espera el siguiente código de la app y escríbelo.'
        : 'Correo o clave incorrectos.'
    case 'no-habilitada':
      return (resultado.estado && CUENTA_NO_HABILITADA[resultado.estado]) ?? resultado.mensaje
    case 'bloqueada':
      return resultado.segundos
        ? `La cuenta está bloqueada por intentos fallidos. Podrás ingresar en ${minutos(resultado.segundos)}.`
        : 'La cuenta está bloqueada por intentos fallidos. Espera unos minutos.'
    case 'esperar':
      return esperar(resultado.segundos)
    case 'rechazado':
      return resultado.mensaje
    case 'sin-red':
      return SIN_RED
    case 'fallo':
      return FALLO
  }
}

export function mensajeSegundoFactor(resultado: ResultadoAlta | ResultadoConfirmacionFactor): string | null {
  switch (resultado.tipo) {
    case 'alta':
    case 'activado':
    case 'sesion-terminada':
      return null
    case 'ya-activo':
      return 'Esta cuenta ya tiene un segundo factor activo. Para cambiar de teléfono, entra al panel y ve a Seguridad.'
    case 'sin-alta':
      return 'El código QR ya no está vigente. Genera uno nuevo y escanéalo otra vez.'
    case 'codigo-incorrecto':
      return 'El código no coincide. Revisa que la hora del teléfono sea la correcta y escribe el código que la app muestra ahora.'
    case 'esperar':
      return esperar(resultado.segundos)
    case 'sin-red':
      return SIN_RED
    case 'fallo':
      return FALLO
  }
}
