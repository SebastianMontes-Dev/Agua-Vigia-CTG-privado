# R8 · Cuentas y seguridad

**Objetivo:** mover a `cuentas/` todo lo que es identidad:
- ingreso del veedor con JWT y TOTP
- su cuenta y la administración de usuarios con permisos
- la auditoría
- las cuentas públicas (registro, verificación, invitación, restablecer, enlaces HTML, reenvío)
- el vecino registrado
- la configuración de seguridad

Riesgo: **alto**. Es la funcionalidad más grande (126 archivos) y la de seguridad: un error aquí abre una ruta o bloquea el
panel. Esfuerzo: 3 sesiones. Se puede partir en R8a (seguridad y veedor) y R8b (cuentas públicas y vecino).

## Estructura destino

```
cuentas/
├── seguridad/
│   ├── SecurityConfig.java            (rutas denegadas por defecto, CORS por perfil: IGUAL)
│   ├── JwtAuthenticationFilter · JwtProvider · ContextoHttp · ContextoDeAccion
│   ├── SesionService.java             AutenticarUsuario + CerrarSesion + EmisorDeSesionPort + RevocacionSesion (Redis, falla cerrado)
│   ├── ControlDeIntentos.java         ControlIntentosPort + RedisControlIntentosAdapter (INCR/SETNX literal)
│   ├── Totp.java                      SegundoFactorPort + TotpAdapter + Base32 + GeneradorSecretosSeguroAdapter
│   ├── Claves.java                    CifradorClavePort + BCryptCifradorClaveAdapter + TiempoConstanteAdapter
│   └── ValidacionDeSecretos (si no se movió en R1)
├── Usuario.java                       @Document("usuarios") + UsuarioId, EstadoCuenta, RolVeedor, Permiso, PermisosEfectivos,
│                                      AlcanceSesion, SesionAutenticada, SesionEmitida, SegundoFactor, SecretoTotp, ClaveEnClaro,
│                                      ClaveHash, Consentimiento*, CorreoYaRegistradoException
├── UsuarioAlmacen.java                (insertarSinteticasSiNoExisten con bulkOps UNORDERED, literal)
├── TokenCuenta.java                   @Document("tokens_cuenta") + TipoTokenCuenta + almacén (marcarUsadoSiVigente, invalidarVigentes literales)
├── veedor/
│   ├── VeedorAuthController · SegundoFactorController · CuentaPropiaController · AdminUsuariosController
│   ├── SegundoFactorService · CuentaPropiaService (CambiarClave) · AdminUsuariosService (Administrar + Consultar + Invitar)
│   ├── Auditoria.java                 @Document("auditoria_cuentas") + AccionAuditada, AuditoriaId, EventoAuditoria, RegistroDeAuditoria, almacén
│   └── BloqueoDeAdministradores.java  @Document("bloqueos_administracion") (findAndModify + upsert literal)
├── publicas/
│   ├── CuentaPublicaController · EnlacesDeCuentaController · ReenvioDeEnlacesController
│   ├── CuentaPublicaService           Registrar, VerificarCorreo, AceptarInvitacion, RestablecerClave, ReenviarEnlace, EmisorDeTokensDeCuenta
│   └── NotificacionCuenta.java        interfaz: MailCuentaAdapter · CorreoDeCuentaDescartadoAdapter (2 implementaciones reales)
├── vecino/
│   ├── CuentaVecinoController · VecinoController
│   ├── VecinoService                  RegistrarVecino, VerificarBarrioVecino, ActualizarPerfilVecino
│   └── CupoPorCuenta.java             CupoPorCuentaPort + RedisCupoPorCuentaAdapter + ResultadoDeCupo
├── CuentasDtos.java                   los 19 DTO de cuentas, mismos nombres simples
├── SembradorAdminInicial.java         (ApplicationReadyEvent; imprime la clave del ADMIN inicial, ADR-086)
└── ImportadorDeVecinosSinteticos.java (+ ImportarVecinosSinteticosService; ADR-094)
```

**Puertos que caen:**
- 16 de entrada.
- 12 de salida, salvo `NotificacionCuenta`, que se queda como interfaz.
- Siguen vivos solo si `reportes` (R4) todavía los usa: se cambian por las clases nuevas en esta misma fase.

## Lo delicado

- **`SecurityConfig` no cambia en nada.** Ni el orden de las reglas, ni los `permitAll`, ni la denegación por defecto, ni CORS. Se mueve el archivo y se cambian los `import`. Los tests de seguridad se mueven sin tocar sus casos: `SecurityConfigCorsTest`, `SeguridadPorDefectoTest`, `CorsPorPerfilTest`, `SinProxyDeConfianzaTest`, `JwtAuthenticationFilterTest`.
- **Las reglas ArchUnit de `@PreAuthorize`** (panel y vecino) tienen que seguir encontrando las rutas. Si filtran por paquete `..api..`, se ajustan a `@RestController` en cualquier paquete, sin aflojarlas.
- **Bloqueo y tiempo constante:**
  - el bloqueo de login va por `correo|ip` con tope global por cuenta
  - una cuenta invitada sin clave gasta el tiempo de un BCrypt
  - la revocación de sesión falla cerrado si Redis cae

  Todo idéntico.
- **TOTP:** mismo algoritmo, ventana y formato de secreto. El secreto sigue en claro en Mongo (riesgo aceptado, docs/07 §3). `scripts/codigo-totp.mjs` y `restablecer-admin.mjs` tienen que seguir funcionando.
- **Los enlaces HTML de `/api/cuentas/enlaces/*`** devuelven el mismo HTML y los mismos códigos.

## Terminado cuando

- La puerta está en verde. `verificar-flujos.mjs` recorre el ingreso con TOTP (alta, confirmación, baja), administración de usuarios, invitación con enlace, registro y verificación, restablecer clave, y el vecino completo.
- `restablecer-admin.mjs` y `codigo-totp.mjs` funcionan contra la base local.
- `verificar-datos.mjs` muestra `usuarios`, `tokens_cuenta`, `auditoria_cuentas` y `bloqueos_administracion` sin cambios.

## Prompt para Claude Code

```
Lee docs/reduccion/README.md, docs/reduccion/invariantes.md y docs/reduccion/R8-cuentas-y-seguridad.md.
Ejecuta R8 en refactor/reduccion-backend (antes: git merge main), en dos tandas si hace falta (R8a seguridad y veedor;
R8b cuentas públicas y vecino), cada una con la puerta en verde. SecurityConfig se mueve sin cambiar una sola regla;
TOTP, bloqueo, tiempo constante y revocación idénticos; operaciones atómicas literales. Tests de seguridad sin tocar sus casos.
Corre la puerta completa y muéstrame la salida. No hagas merge a main sin que yo lo confirme. Si usas subagentes, usa model sonnet.
```
