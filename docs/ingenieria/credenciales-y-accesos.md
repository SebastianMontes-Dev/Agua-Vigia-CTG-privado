# Credenciales y accesos — inventario (sin valores)

> **Qué es esto.** La lista de todo lo que en AguaVigía pide una clave, un secreto o un token: qué es, para qué
> sirve, **dónde vive su valor** y cómo se recupera o se regenera. **Aquí nunca van los valores.** Un valor escrito
> en un archivo versionado queda en el historial de git para siempre aunque se borre después, y `gitleaks` lo
> marcaría. Los valores viven en `.env` (ignorado por git) y en tu gestor de contraseñas.
>
> **Última actualización:** 2026-09-24

---

## 1. Dónde guardar los valores para no perderlos

| Qué | Dónde | Por qué ahí |
|---|---|---|
| Valores reales de las variables de `.env` | **Tu gestor de contraseñas** (una nota «AguaVigía — .env») **y** el propio `.env` local | El `.env` no se versiona: si se borra la carpeta, sin copia se pierde |
| Secreto TOTP del ADMIN (segundo factor) | Tu gestor de contraseñas **y** tu app de autenticador | Solo se muestra una vez, al dar de alta el segundo factor |
| Token de GitHub (`GITHUB_PERSONAL_ACCESS_TOKEN`) | GitHub → Settings → Developer settings; copia en el gestor | GitHub solo lo muestra al crearlo |

**Regla del proyecto:** ni el agente ni ningún archivo del repositorio guardan estos valores. Cuando una prueba
necesita la clave del administrador, la escribes tú en tu terminal (`ADMIN_CLAVE='…' node scripts/verificar-flujos.mjs`)
y le pasas al agente solo la salida, sin la clave.

## 2. Inventario

| Nombre | Para qué | Dónde vive el valor | Si se pierde |
|---|---|---|---|
| `JWT_SECRET` | Firma los tokens de sesión del panel | `.env` | Generar otro (`openssl rand -base64 32`). Invalida las sesiones abiertas; nada más |
| `VEEDOR_PASSWORD_HASH` | Hash BCrypt de la clave del **primer ADMIN**. Del hash no se recupera la clave | `.env` (cada `$` escapado como `$$`) | Ver §3 |
| `ADMIN_INICIAL_CORREO` | Correo de esa cuenta ADMIN | `.env` | No es secreto; lo eliges |
| `IOT_KEY` | Clave de los sensores IoT (`POST /api/iot/presion`) | `.env` | Vacía = el endpoint responde 503. Inventar otra y ponerla en los sensores |
| `TELEGRAM_BOT_TOKEN` | Token del bot de Telegram (RF041); lo entrega `@BotFather` | `.env` y tu gestor de contraseñas | Revocarlo con `/revoke` en `@BotFather` y pedir otro. Vacío = el canal queda apagado |
| `GITHUB_PERSONAL_ACCESS_TOKEN` | Lo usa el servidor MCP de GitHub (`.mcp.json` lo lee como `${GITHUB_PERSONAL_ACCESS_TOKEN}`) | `.env` y GitHub | Revocarlo en GitHub y crear otro |
| **Segundo factor (TOTP) del ADMIN** | Segunda comprobación del login del panel | Autenticador y gestor | Si se pierde: `scripts/restablecer-admin.mjs` (§3) |
| Cuentas `VEEDOR`/`OBSERVADOR` sembradas | Cuentas de demostración | Clave pública de demo, ver §4 | Resembrar |

`.env.example` es la plantilla con los nombres y comentarios de cada variable.

## 3. La clave del ADMIN — situación al 2026-09-24

- **Cuenta:** `veedor@aguavigia.local`, rol ADMIN, creada al arrancar el backend con `ADMIN_INICIAL_CORREO` y
  `VEEDOR_PASSWORD_HASH` sobre una colección `usuarios` vacía. **La clave la eligió el dueño; no consta en el
  repositorio** (guárdala en el gestor de contraseñas). Segundo factor (TOTP) **ya dado de alta**; su secreto tampoco
  consta aquí.
- **Historia:** la ADMIN anterior (`admin.demo@aguavigia.example`) tenía una clave que ya no se recordaba (el `.env`
  guardaba solo su hash y la documentada dio `401`). Se vació `usuarios` y se resembró: 1 ADMIN + 40 000 cuentas de
  demostración. El respaldo de las 20 001 cuentas anteriores quedó solo en la carpeta temporal de la sesión del agente
  (no es una copia duradera).
- **El ADMIN solo se crea si `usuarios` está vacía** al arrancar el backend con esas dos variables. Para cambiar la
  clave de un ADMIN existente, `scripts/restablecer-admin.mjs --clave-del-env` (abajo): ya no hace falta vaciar `usuarios`.
- **Hash de una clave nueva:** `docs/ingenieria/entorno-local.md` §4 (`GenerarHashVeedor`); cada `$` va como `$$` en el `.env`.
- **Si se pierde el secreto TOTP o la clave:** `node scripts/restablecer-admin.mjs --correo <correo del ADMIN>` borra el
  segundo factor; con `--clave-del-env` además fija como clave la de `VEEDOR_PASSWORD_HASH`. La siguiente sesión entra con
  alcance `ALTA_SEGUNDO_FACTOR` y solo sirve para dar de alta un TOTP nuevo. Solo corre contra una base local y deja el
  evento en `auditoria_cuentas` (comprobado el 2026-09-29 con una cuenta ADMIN temporal).

## 4. Claves públicas de demostración (ya están en el repositorio)

No son secretos: sirven solo contra una base local de demostración.

| Clave | Dónde está documentada | Sirve para |
|---|---|---|
| `DemoAguaVigia-2026` | `scripts/sembrar-usuarios-demo.mjs` | Entrar como `VEEDOR`/`OBSERVADOR` de las cuentas ACTIVAS sembradas. **No hay ADMIN entre ellas** |
| `AguaVigia-Dev-2026` | `docs/ingenieria/entorno-local.md` | Clave de desarrollo que documenta esa guía; **no es la del ADMIN actual** (dio `401` sobre el anterior) |

Los correos sembrados usan dominios reales (`hotmail.com`, `live.com`…): no los uses con un SMTP real.

## 5. Accesos sin clave (por si se buscan)

| Qué | Dirección |
|---|---|
| API | `http://localhost:8081` |
| Swagger | `http://localhost:8081/swagger-ui.html` |
| MailHog (correo de pruebas) | `http://localhost:8025` |
| Mongo local | `mongodb://localhost:27017/?directConnection=true` (sin clave en local) |

## 6. Cuando cambie algo

Quien cree, rote o pierda una credencial actualiza este archivo **en el mismo cambio**, sin el valor: qué es,
dónde vive y cómo se recupera.
