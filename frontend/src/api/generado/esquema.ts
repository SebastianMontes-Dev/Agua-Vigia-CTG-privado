// Generado desde backend/openapi.yaml con `npm run api:sync`. No se edita a mano.

export interface paths {
    "/api/veedor/usuarios/{id}/invitacion/reenvio": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reenviar la invitación a una cuenta que aún no la aceptó
         * @description Requiere GESTIONAR_USUARIOS. El enlace anterior deja de servir y se reinicia su vigencia de 7 días.
         */
        post: operations["reenviarInvitacion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/usuarios/invitaciones": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Invitar a una persona con un rol ya decidido
         * @description Crea la cuenta en INVITADA y le envia un enlace para que fije su clave. Al
         *     aceptarlo queda ACTIVA sin necesitar otra aprobacion.
         */
        post: operations["invitar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/sesion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Iniciar sesion en el panel del veedor
         * @description Devuelve un token JWT valido por 8 horas (RNF011) junto con el rol y los permisos
         *     ya resueltos. Si la cuenta tiene segundo factor y no se envio `codigoTotp`, la
         *     respuesta es 401 con type `segundo-factor-requerido`: hay que reintentar con el
         *     codigo, no es un error de credencial.
         */
        post: operations["iniciarSesion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/sesion/cierre": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cerrar sesion
         * @description Revoca en el servidor todas las sesiones vivas de la cuenta, no solo la de este
         *     navegador. Un token copiado antes del cierre deja de servir en el acto.
         */
        post: operations["cerrar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/segundo-factor/confirmacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Confirmar el alta con un codigo de la app
         * @description Devuelve una sesion nueva de alcance COMPLETO. Es lo que permite que un ADMIN
         *     recien sembrado pase de su sesion restringida al panel sin volver a escribir la
         *     clave que acaba de escribir.
         */
        post: operations["confirmar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/segundo-factor/baja": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Desactivar el segundo factor de la propia cuenta
         * @description Exige un codigo valido: si bastara con la sesion, un token robado podria quitar
         *     de en medio justamente la defensa que impide usarlo. Un ADMIN no puede
         *     desactivarlo, su rol lo exige.
         */
        post: operations["desactivar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/segundo-factor/alta": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Empezar el alta: genera el secreto y devuelve el QR
         * @description El secreto queda guardado sin confirmar y todavia no se exige al entrar. Solo
         *     empieza a hacerlo tras confirmar un codigo valido. El secreto se muestra una
         *     sola vez: no hay endpoint para volver a leerlo.
         */
        post: operations["iniciar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/cuenta/clave": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cambiar la propia clave
         * @description Exige la clave actual y comparte el contador de intentos fallidos con el inicio de sesión
         *     (5 fallos en 15 minutos bloquean la cuenta 15 minutos). Al cambiarla se cierran **todas** las
         *     sesiones, la actual incluida: el cliente debe volver a pedir `POST /api/veedor/sesion` con la
         *     clave nueva. Se avisa por correo del cambio.
         */
        post: operations["cambiarClave"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/cortes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Listar los cortes que afectan a un sector */
        get: operations["listarPorSector"];
        put?: never;
        /**
         * Registrar un corte oficial
         * @description Sectores afectados, inicio, fin prometido y causa (RF016). Origen VEEDOR.
         */
        post: operations["registrar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/suscripciones": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Suscribirse a los avisos de uno o más sectores
         * @description Crea la suscripción en PENDIENTE_CONFIRMACION y envía un correo de doble
         *     opt-in (Ley 1581/2012, RF013). No empieza a recibir avisos hasta confirmarla.
         */
        post: operations["suscribirse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/suscripciones/confirmar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pantalla del enlace «Confirmar» del correo
         * @description Página a la que lleva el enlace del correo. Solo muestra un botón: NO confirma nada, porque un
         *     antivirus o una vista previa de enlaces abre los GET sin que nadie los pida (`ADR-054`). La
         *     acción ocurre al enviar el formulario, que hace POST a la misma ruta.
         */
        get: operations["pantallaConfirmar"];
        put?: never;
        /**
         * Confirmar la suscripción (doble opt-in)
         * @description Acción del botón de la página de confirmación (o de un cliente de API). El token es de un solo enlace, no de un solo uso:
         *     confirmarla dos veces no falla (RF013). Responde JSON o una página HTML de cortesía
         *     según el `Accept` de quien pide: el formulario de la página responde HTML y un cliente de API
         *     responde JSON. `token` va como parámetro de consulta o del formulario.
         */
        post: operations["confirmar_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/suscripciones/cancelar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pantalla del enlace de baja de todo correo (RF015)
         * @description Página a la que lleva el enlace del correo. Solo muestra un botón: NO cancela nada, porque un
         *     antivirus o una vista previa de enlaces abre los GET sin que nadie los pida (`ADR-054`). La
         *     acción ocurre al enviar el formulario, que hace POST a la misma ruta.
         */
        get: operations["pantallaCancelar"];
        put?: never;
        /**
         * Darse de baja en un clic (RF015)
         * @description Acción del botón de la página de baja (o de un cliente de API). Sin pedir credenciales: el token que llega en cada correo es suficiente. Responde
         *     JSON o una página HTML de cortesía según el `Accept` de quien pide (mismo motivo
         *     que en {@code /confirmar}).
         */
        post: operations["cancelar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/reportes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Registrar un reporte ciudadano
         * @description Sin registro ni cuenta (RF005). Limita automáticamente los reportes por
         *     dispositivo en la ventana vigente (RF006) — ver 429. Hace falta el `sectorId`, la
         *     `coordenada` o ambos (RF007): con solo la coordenada el servidor infiere el sector
         *     que la contiene y responde 400 si cae fuera de todo barrio de Cartagena. La
         *     coordenada se envía solo si el usuario autorizó compartir su ubicación.
         */
        post: operations["registrar_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/reportes/{id}/foto": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Agregar evidencia a un reporte
         * @description Permite subir una foto y asociarla a un reporte existente (M10).
         */
        post: operations["agregarEvidencia"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/reportes/{id}/confirmar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Confirmar un reporte
         * @description Permite a otro vecino confirmar un reporte ciudadano (M11).
         */
        post: operations["confirmar_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/iot/presion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Registrar una lectura de presión de un sensor
         * @description Una presión por debajo del umbral (15 psi por defecto) se registra como reporte de
         *     PRESION_BAJA con el cupo de sensor. Una lectura normal responde 200 sin registrar
         *     nada. Sin cuerpo de respuesta en el caso exitoso.
         */
        post: operations["reportarPresion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cuentas/verificacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Confirmar el correo con el token del enlace
         * @description Pasa la cuenta a PENDIENTE_APROBACION. Sigue sin poder entrar hasta que un ADMIN la apruebe.
         */
        post: operations["verificarCorreo"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cuentas/verificacion/reenvio": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reenviar el correo de verificación
         * @description Para quien se registró y no recibió el enlace (o venció). Responde 202 **siempre**, exista o no
         *     la cuenta y ya esté verificada o no: no revela qué correos están registrados. Reenvía como mucho
         *     una vez cada 2 minutos por cuenta. El enlace anterior deja de servir.
         */
        post: operations["reenviarVerificacion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cuentas/restablecimiento": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Pedir el enlace para restablecer la clave
         * @description Responde 202 siempre, exista o no la cuenta. Ver el javadoc de esta clase.
         */
        post: operations["pedirRestablecimiento"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cuentas/registro": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Solicitar una cuenta del panel
         * @description Crea la cuenta en PENDIENTE_VERIFICACION y envia el enlace de confirmacion.
         *     Registrarse no concede ningun permiso: hace falta verificar el correo y que un
         *     ADMIN apruebe. Responde 202 aunque el correo ya tenga cuenta, para no revelar
         *     que direcciones estan registradas.
         */
        post: operations["registrarse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cuentas/invitacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Aceptar una invitacion fijando la clave
         * @description Deja la cuenta ACTIVA con el rol que eligio quien invito. No hace falta otra aprobacion.
         */
        post: operations["aceptar"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cuentas/enlaces/verificar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pantalla del enlace «Confirmar mi correo»
         * @description Muestra el botón de confirmación. No consume el token: eso ocurre al enviar el formulario.
         */
        get: operations["pantallaVerificar"];
        put?: never;
        /**
         * Confirmar el correo desde el formulario de la pantalla
         * @description Mismo efecto que POST /api/cuentas/verificacion, pero recibe el formulario y responde HTML.
         */
        post: operations["verificarCorreo_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cuentas/enlaces/restablecer": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pantalla del enlace para restablecer la clave
         * @description Muestra el formulario para elegir la clave nueva. No consume el token.
         */
        get: operations["pantallaRestablecer"];
        put?: never;
        /**
         * Restablecer la clave desde el formulario de la pantalla
         * @description Mismo efecto que POST /api/cuentas/clave, pero recibe el formulario y responde HTML.
         */
        post: operations["restablecerClave"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cuentas/enlaces/invitacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Pantalla del enlace de invitación
         * @description Muestra el formulario para elegir la clave. No consume el token.
         */
        get: operations["pantallaInvitacion"];
        put?: never;
        /**
         * Aceptar la invitación desde el formulario de la pantalla
         * @description Mismo efecto que POST /api/cuentas/invitacion, pero recibe el formulario y responde HTML.
         */
        post: operations["aceptarInvitacion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cuentas/clave": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Fijar la clave nueva con el token del enlace
         * @description Cambia la clave y revoca todas las sesiones abiertas de esa cuenta.
         */
        post: operations["fijarClave"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/usuarios/{id}/suspension": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Suspender una cuenta activa
         * @description Revoca sus sesiones al instante: no espera a que caduque su token.
         */
        patch: operations["suspender"];
        trace?: never;
    };
    "/api/veedor/usuarios/{id}/rechazo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Denegar una solicitud de acceso */
        patch: operations["rechazar"];
        trace?: never;
    };
    "/api/veedor/usuarios/{id}/reactivacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Devolver el acceso a una cuenta suspendida */
        patch: operations["reactivar"];
        trace?: never;
    };
    "/api/veedor/usuarios/{id}/permisos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Cambiar rol y ajustes de permisos de una cuenta
         * @description Revoca las sesiones vivas de esa persona, tanto si los permisos se amplian como
         *     si se recortan: el token los lleva dentro y una sesion abierta seguiria usando
         *     los anteriores.
         */
        patch: operations["cambiarPermisos"];
        trace?: never;
    };
    "/api/veedor/usuarios/{id}/aprobacion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Aprobar una cuenta que ya verifico su correo, asignandole permisos */
        patch: operations["aprobar"];
        trace?: never;
    };
    "/api/veedor/reportes/{id}/descartar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Descartar un reporte */
        patch: operations["descartar"];
        trace?: never;
    };
    "/api/veedor/reportes/{id}/aprobar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Aprobar un reporte */
        patch: operations["aprobar_1"];
        trace?: never;
    };
    "/api/veedor/ingesta/propuestas/{id}/descartar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Descartar una propuesta
         * @description No toca el sector. La propuesta se archiva como descartada, no se borra.
         */
        patch: operations["descartar_1"];
        trace?: never;
    };
    "/api/veedor/ingesta/propuestas/{id}/aprobar": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /**
         * Aprobar una propuesta
         * @description Aplica el estado propuesto al sector y anexa el evento a la bitácora pública
         *     (RF026). Es el único camino por el que la ingesta llega al mapa.
         */
        patch: operations["aprobar_2"];
        trace?: never;
    };
    "/api/veedor/cortes/{id}/cierre": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Cerrar un corte con la hora real de restablecimiento (RF017) */
        patch: operations["cerrar_1"];
        trace?: never;
    };
    "/sin-proteger": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["sinProteger"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/protegida": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["protegida"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/yo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Datos de la cuenta que tiene la sesion
         * @description Lo usa el frontend al recargar para saber que puede pintar sin volver a pedir la
         *     clave. Devuelve el estado vigente en la base de datos, no lo que dice el token.
         */
        get: operations["yo"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/usuarios": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar cuentas, mas recientes primero
         * @description Paginado, con el total y el enlace a la siguiente pagina en `X-Total-Count` y
         *     `Link`. `estado` filtra por PENDIENTE_APROBACION para ver solo la cola de altas.
         */
        get: operations["listar"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/reportes/pendientes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar los reportes pendientes de moderación, más antiguos primero
         * @description Paginado, con el total y el enlace a la siguiente página en las cabeceras
         *     `X-Total-Count` y `Link`. Por defecto 50; el máximo por página es 200.
         */
        get: operations["listarPendientes"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/ingesta/salud": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Salud de cada colector: última ejecución exitosa, ítems y tasa de error
         * @description Lista vacía mientras el pipeline no haya corrido un ciclo. La telemetría vive en
         *     memoria del proceso, así que un reinicio la reinicia.
         */
        get: operations["salud"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/ingesta/propuestas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar las propuestas pendientes de revisión, más recientes primero
         * @description Paginado, con el total y el enlace a la siguiente página en las cabeceras
         *     `X-Total-Count` y `Link`. Por defecto 50; el máximo por página es 200.
         */
        get: operations["listarPendientes_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/ingesta/fallidos": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar los documentos que siguen fallando al procesarse, más recientes primero
         * @description Un documento sale de esta lista en cuanto se procesa con éxito: es lo que sigue
         *     roto *ahora*, no un histórico. Máximo 200 filas.
         */
        get: operations["fallidos"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/cortes/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Consultar un corte por su identificador */
        get: operations["consultar"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/veedor/auditoria": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Bitacora de auditoria de cuentas, mas recientes primero
         * @description Solo anexado: no hay forma de editar ni borrar un asiento desde la API.
         */
        get: operations["auditoria"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v2/requests.json": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar los sectores con el servicio afectado, en formato Open311
         * @description Un `service_request` por sector que no está en CON_SERVICIO. Los sectores sin
         *     estado verificado no aparecen: publicar "sin novedad" sin haberlo comprobado es
         *     el falso positivo que ADR-014 evita.
         */
        get: operations["getRequests"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sectores": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar los sectores con su estado conocido
         * @description Devuelve los sectores de Cartagena (211 barrios sembrados desde el GeoJSON
         *     oficial). `estado` viaja nulo mientras no haya dato verificado del sector —
         *     el cliente debe mostrarlo como "sin datos" y no suponer que hay servicio.
         */
        get: operations["listarSectores"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sectores/{sectorId}/cortes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Histórico de cortes de un sector, del más reciente al más antiguo
         * @description Cortes oficiales que afectaron al sector, abiertos y cerrados. Paginado con las mismas
         *     cabeceras que la bitácora (`X-Total-Count`, `X-Total-Pages`, `X-Page`, `X-Page-Size`, `Link`);
         *     por defecto 50, máximo 200. Un sector sin cortes devuelve una lista vacía, no un 404.
         */
        get: operations["listar_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sectores/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Consultar un sector por su identificador */
        get: operations["consultarSector"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sectores/stream": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Avisos en vivo de cambios de estado (SSE)
         * @description Conexión abierta (`text/event-stream`) que AVISA de que algo cambió; no envía el
         *     estado. Cada evento `sectores` trae `{"actualizadoEn": "..."}` y el cliente pide
         *     entonces GET /api/sectores, que está cacheado. Un comentario `:latido` llega cada
         *     25 s y `retry:` indica cuánto esperar antes de reconectar. El servidor agrupa los
         *     cambios en un aviso por segundo como máximo y cierra la conexión cada ~10-12
         *     minutos por diseño. Por encima del tope de conexiones responde 429 con `Retry-After`.
         */
        get: operations["streamSectores"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/sectores/geometria": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Polígonos de todos los sectores (GeoJSON)
         * @description FeatureCollection con un Feature por sector; `id` es el mismo que devuelve
         *     GET /api/sectores. Los polígonos solo cambian al volver a sembrar, por eso la
         *     respuesta se puede cachear un día.
         */
        get: operations["geometria"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/estadisticas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Obtener estadísticas globales de la ciudad */
        get: operations["globales"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/estadisticas/exportar.csv": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Exportar las estadísticas en CSV (RF025)
         * @description Separador `;` y BOM UTF-8, para que Excel en español lo abra sin romper las tildes.
         */
        get: operations["exportarCsv"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cumplimiento": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Índice global de la ciudad, sobre todos los cortes cerrados */
        get: operations["global"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cumplimiento/serie": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Evolución del índice mes a mes (RF024)
         * @description Un punto por mes con al menos un corte cerrado, en hora de Cartagena. Sin
         *     `sectorId`, la ciudad completa. `desde` y `hasta` son opcionales y acotan por la
         *     hora real de restablecimiento. Lista vacía si no hay cortes cerrados en el
         *     rango — una serie sin datos es una respuesta válida.
         */
        get: operations["serie"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cumplimiento/serie.csv": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * La misma serie en CSV (RF025)
         * @description Separador `;` y BOM UTF-8, para que Excel en español la abra sin romper las tildes.
         */
        get: operations["serieCsv"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cumplimiento/sectores/{sectorId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Índice agregado de un sector, sobre sus cortes cerrados */
        get: operations["porSector"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cumplimiento/cortes/{corteId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Índice de un corte cerrado */
        get: operations["porCorte"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/bitacora": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar los eventos de la bitácora, más recientes primero
         * @description Paginado: la bitácora es de solo anexado (RF028), así que crece sin cota.
         *     El total, la página y el enlace a la siguiente viajan en las cabeceras
         *     `X-Total-Count`, `X-Total-Pages`, `X-Page`, `X-Page-Size` y `Link` — el cuerpo
         *     sigue siendo un arreglo JSON, así que un cliente que las ignore no se rompe.
         *     Por defecto 50 eventos; el máximo por página es 200.
         *
         *     Filtros opcionales, que se combinan y buscan en todo el historial: `sectorId`,
         *     `tipo`, `desde` (inclusivo) y `hasta` (exclusivo), ambos instantes ISO 8601 en
         *     UTC. El enlace `Link` a la siguiente página conserva los filtros. Sin
         *     coincidencias, la respuesta es una página vacía, no un error; un `tipo` que no
         *     existe o un `hasta` que no es posterior a `desde` son un 400.
         */
        get: operations["listar_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/bitacora/{id}/sustento": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Los reportes que sustentan un evento de consenso (RF011)
         * @description Ids de los reportes ciudadanos que sostuvieron el cambio de estado, para contrastarlo con la
         *     evidencia. Van aparte del listado porque en una avería grande pueden ser miles. Paginado con las
         *     mismas cabeceras que el listado; por defecto 50 ids por página, máximo 200. Vacío en los
         *     eventos que no son de consenso.
         */
        get: operations["sustento"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /** @description Invitacion emitida por un ADMIN: crea la cuenta con su rol y manda el enlace */
        SolicitudInvitacion: {
            /** Format: email */
            correo: string;
            nombre: string;
            /** @description ADMIN, VEEDOR u OBSERVADOR */
            rol: string;
        };
        /** @description Cuenta del panel, tal como la ve un ADMIN */
        UsuarioRespuesta: {
            id?: string;
            correo?: string;
            nombre?: string;
            /** @description PENDIENTE_VERIFICACION, PENDIENTE_APROBACION, INVITADA, ACTIVA, SUSPENDIDA o RECHAZADA */
            estado?: string;
            rol?: string;
            permisosEfectivos?: string[];
            permisosConcedidos?: string[];
            permisosRevocados?: string[];
            segundoFactorActivo?: boolean;
            /** Format: date-time */
            creadoEn?: string;
            /** Format: date-time */
            actualizadoEn?: string;
        };
        /** @description Credencial de acceso al panel del veedor */
        CredencialVeedor: {
            /**
             * Format: email
             * @description Correo de la cuenta
             * @example veedor@ejemplo.org
             */
            correo: string;
            /** @description Clave de la cuenta */
            clave: string;
            /**
             * @description Codigo de 6 digitos de la app de autenticacion. Se omite en el primer intento; si la
             *     cuenta tiene segundo factor, la respuesta 401 con type `segundo-factor-requerido`
             *     indica que hay que reintentar incluyendolo.
             */
            codigoTotp?: string;
        };
        /** @description Sesion emitida para el panel del veedor (RNF011: expira en 8 horas) */
        SesionVeedor: {
            /** @description Token JWT. Se envia como 'Authorization: Bearer <token>' */
            token?: string;
            usuarioId?: string;
            nombre?: string;
            correo?: string;
            /** @description ADMIN, VEEDOR u OBSERVADOR */
            rol?: string;
            /** @description Permisos efectivos ya resueltos: rol mas concedidos menos revocados */
            permisos?: string[];
            /**
             * @description COMPLETO, o ALTA_SEGUNDO_FACTOR cuando la cuenta es ADMIN y todavia no dio de alta su
             *     TOTP. Con ese alcance el token solo sirve para /api/veedor/segundo-factor.
             */
            alcance?: string;
        };
        /** @description Codigo de 6 digitos de la app de autenticacion */
        SolicitudCodigo: {
            codigo: string;
        };
        /** @description Datos para dar de alta el segundo factor. El secreto solo se muestra aqui, una vez. */
        AltaSegundoFactorRespuesta: {
            /** @description URI otpauth:// para pintar el QR */
            uri?: string;
            /** @description El mismo secreto en Base32, para teclearlo si la camara falla */
            secreto?: string;
        };
        /** @description Cambiar la propia clave con la sesión iniciada */
        SolicitudCambioClave: {
            /** @description La clave de hoy. Sin ella un token robado bastaría para cambiarla. */
            claveActual: string;
            /** @description La nueva: de 12 a 128 caracteres y distinta de la actual. */
            claveNueva: string;
        };
        /** @description Registro de un corte oficial por el veedor (RF016) */
        SolicitudCorte: {
            /**
             * @description Identificadores de los sectores afectados
             * @example [
             *       "manga",
             *       "bocagrande"
             *     ]
             */
            sectoresAfectados: string[];
            /** Format: date-time */
            inicio: string;
            /** Format: date-time */
            finPrometido: string;
            /** @example Mantenimiento planta El Bosque */
            causa: string;
        };
        ProblemDetail: {
            /** Format: uri */
            type?: string;
            title?: string | null;
            /** Format: int32 */
            status?: number;
            detail?: string | null;
            /** Format: uri */
            instance?: string | null;
            properties?: {
                [key: string]: Record<string, never>;
            } | null;
        };
        /** @description Corte oficial (RF016-RF017) */
        CorteRespuesta: {
            id?: string;
            sectoresAfectados?: string[];
            /** Format: date-time */
            inicio?: string;
            /** Format: date-time */
            finPrometido?: string;
            /**
             * Format: date-time
             * @description Nulo mientras el corte sigue abierto
             */
            finReal?: string;
            causa?: string;
            origen?: string;
            estado?: string;
        };
        /** @description Solicitud para suscribirse a los avisos de uno o más sectores */
        SolicitudSuscripcion: {
            /**
             * Format: email
             * @description Correo al que llegarán los avisos
             * @example vecino@correo.com
             */
            correo: string;
            /** @description Identificadores de los sectores a seguir */
            sectorIds: string[];
        };
        /** @description Suscripción creada, pendiente de confirmación por correo (RF013) */
        SuscripcionRespuesta: {
            id?: string;
            correo?: string;
            sectorIds?: string[];
            /** @description PENDIENTE_CONFIRMACION, CONFIRMADA o CANCELADA */
            estado?: string;
            /** Format: date-time */
            creadaEn?: string;
        };
        /** @description Coordenada GPS del reporte, solo cuando el usuario la autoriza (RF007) */
        CoordenadaDTO: {
            /** Format: double */
            latitud: number;
            /** Format: double */
            longitud: number;
        };
        /** @description Reporte ciudadano sin registro (RF005-RF008) */
        SolicitudReporte: {
            /**
             * @description Identificador del sector reportado. Opcional si viaja la coordenada: entonces el
             *     servidor infiere el barrio que la contiene (RF007). Si no viaja ninguno, 400.
             * @example bocagrande
             */
            sectorId?: string | null;
            /**
             * @description SIN_AGUA, PRESION_BAJA o SERVICIO_RESTABLECIDO
             * @example SIN_AGUA
             */
            tipo: string;
            /**
             * @description Huella anónima del dispositivo (ADR-007) — no es una cuenta ni un identificador
             *     personal. El cliente la genera una vez (p. ej. un UUID persistido en el dispositivo,
             *     hasheado) y la reutiliza en cada reporte; es lo único que permite RF006 (límite de
             *     reportes por dispositivo) sin pedir registro.
             */
            huella: string;
            coordenada?: components["schemas"]["CoordenadaDTO"];
        };
        /** @description Reporte ciudadano registrado */
        ReporteRespuesta: {
            id?: string;
            sectorId?: string;
            tipo?: string;
            /** Format: date-time */
            timestamp?: string;
            fotoUrl?: string;
            /** Format: int32 */
            confirmaciones?: number;
        };
        /** @description Solicitud para confirmar un reporte ciudadano por otro vecino */
        SolicitudConfirmar: {
            /**
             * @description Huella hash del dispositivo del usuario que confirma (ADR-007)
             * @example 3a7b9c...
             */
            huella: string;
        };
        IotCoordenada: {
            /** Format: double */
            lat?: number;
            /** Format: double */
            lon?: number;
        };
        IotPresionRequest: {
            sensorId?: string;
            sectorId?: string;
            /** Format: double */
            presionPsi?: number;
            coordenada?: components["schemas"]["IotCoordenada"];
        };
        /** @description Pedir de nuevo el correo de verificación. Responde siempre 202, exista o no la cuenta. */
        SolicitudReenvioVerificacion: {
            /** Format: email */
            correo: string;
        };
        /** @description Pedir el enlace de restablecimiento. Responde siempre 202, exista o no la cuenta. */
        SolicitudRestablecer: {
            /** Format: email */
            correo: string;
        };
        /** @description Solicitud de acceso al panel. No concede nada: exige verificar el correo y que un ADMIN apruebe. */
        SolicitudRegistro: {
            /** Format: email */
            correo: string;
            /** @description Nombre con el que apareceras en la auditoria del panel */
            nombre: string;
            /** @description Minimo 12 caracteres. La politica completa vive en ClaveEnClaro. */
            clave: string;
        };
        /** @description Fijar clave desde un enlace de un solo uso (invitacion o restablecimiento) */
        SolicitudFijarClave: {
            /** @description Token que venia en el enlace del correo */
            token: string;
            clave: string;
        };
        /**
         * @description Rol de base mas los ajustes por persona. Los permisos del rol se aplican solos; `concedidos`
         *     anade sobre ellos y `revocados` quita. Un permiso en las dos listas es un error y se rechaza.
         */
        SolicitudPermisos: {
            /** @description ADMIN, VEEDOR u OBSERVADOR */
            rol: string;
            concedidos?: string[];
            revocados?: string[];
        };
        /** @description Reporte ciudadano en la cola de moderación del veedor (RF018) */
        ReporteModeracionRespuesta: {
            id?: string;
            sectorId?: string;
            tipo?: string;
            coordenada?: components["schemas"]["CoordenadaDTO"];
            /** Format: date-time */
            timestamp?: string;
            /** @description PENDIENTE, APROBADO o DESCARTADO */
            estadoModeracion?: string;
        };
        /**
         * @description Propuesta de cambio de estado detectada por la ingesta automatizada (M9), esperando la
         *     revisión de un veedor. No afecta el mapa público hasta que se apruebe.
         */
        PropuestaIngestaRespuesta: {
            id?: string;
            sectorId?: string;
            /** @description SIN_SERVICIO, PRESION_BAJA, CORTE_PROGRAMADO o CON_SERVICIO */
            estadoPropuesto?: string;
            /**
             * @description Colector que la detectó
             * @example acuacar
             */
            fuente?: string;
            /** @description Enlace al boletín o nota de prensa original */
            urlOriginal?: string | null;
            /** @description Fragmento del que se dedujo el estado, para que el veedor pueda verificarlo */
            citaTextual?: string;
            /**
             * Format: double
             * @description Entre 0 y 1, graduada según la evidencia que halló el extractor (ADR-032): 0.85 con
             *     enumeración explícita de barrios y horario, 0.75 con enumeración sin horario, 0.45
             *     con una mención suelta en prosa. Sirve para ordenar la cola, no para publicar solo.
             */
            confianza?: number;
            /** Format: date-time */
            detectadaEn?: string;
            /** @description PENDIENTE, APROBADA o DESCARTADA */
            estadoRevision?: string;
            /**
             * Format: date-time
             * @description Inicio de la ventana que el boletín prometió. Nulo cuando el texto no la declaraba:
             *     no se estima (ADR-006).
             */
            inicioDeclarado?: string | null;
            /**
             * Format: date-time
             * @description Fin prometido de la misma ventana. Junto con el inicio es lo que permite que el
             *     estado del sector evolucione solo (ADR-033) y lo que alimenta el Índice de
             *     Cumplimiento (RF020-RF022).
             */
            finPrometido?: string | null;
        };
        /** @description Cierre de un corte con la hora real de restablecimiento (RF017) */
        SolicitudCierreCorte: {
            /** Format: date-time */
            horaReal: string;
        };
        /** @description Estado de salud de un colector de la ingesta automatizada */
        SaludColectorRespuesta: {
            /** @example acuacar */
            nombre?: string;
            /**
             * Format: date-time
             * @description Nulo si el colector todavía no ha completado un ciclo con éxito
             */
            ultimaEjecucionExitosa?: string | null;
            /**
             * Format: date-time
             * @description Nulo si nunca ha fallado
             */
            ultimoFallo?: string | null;
            /** @description Mensaje del último fallo, para diagnosticar sin entrar al servidor */
            motivoDelUltimoFallo?: string | null;
            /**
             * Format: int64
             * @description Documentos traídos desde que arrancó el proceso
             */
            itemsProcesados?: number;
            /**
             * Format: double
             * @description Entre 0 y 1, sobre los ciclos corridos desde que arrancó el proceso
             */
            tasaDeError?: number;
            /**
             * Format: int32
             * @description Ciclos seguidos fallando. Desde 3, el colector se reporta caído en /actuator/health
             */
            fallosConsecutivos?: number;
        };
        /** @description Documento de la ingesta que falló al procesarse y sigue en cola de reintento */
        DocumentoFallidoRespuesta: {
            /** @example acuacar */
            fuente?: string;
            urlOriginal?: string;
            titulo?: string | null;
            /** @description Mensaje de la excepción que hizo fallar el procesamiento */
            motivo?: string;
            /** Format: date-time */
            primerIntento?: string;
            /** Format: date-time */
            ultimoIntento?: string;
            /**
             * Format: int32
             * @description Veces que se reintentó sin éxito, una por ciclo de ingesta
             */
            reintentos?: number;
        };
        /** @description Asiento de la bitacora de auditoria de cuentas: quien le hizo que a quien */
        EventoAuditoriaRespuesta: {
            id?: string;
            accion?: string;
            /** @description Nulo cuando actua el sistema o alguien sin sesion */
            autorCorreo?: string;
            sujetoCorreo?: string;
            detalle?: string;
            ip?: string;
            /** Format: date-time */
            ocurrioEn?: string;
        };
        /** @description service_request de Open311 GeoReport v2 */
        Open311Response: {
            service_request_id?: string;
            /**
             * @description open o closed
             * @example open
             */
            status?: string;
            /**
             * @description Código del tipo de servicio
             * @example AGUA-001
             */
            service_code?: string;
            service_name?: string;
            description?: string;
            /** @description Nombre del barrio. La unidad geográfica es el sector, no un punto (ADR-026) */
            address?: string;
            /**
             * Format: date-time
             * @description Cuándo se registró el estado actual del sector
             */
            requested_datetime?: string | null;
            /**
             * Format: date-time
             * @description Igual a requested_datetime: el estado del sector es su propia actualización
             */
            updated_datetime?: string | null;
        };
        /** @description Listado de sectores con la hora en que el servidor genero la respuesta */
        RespuestaSectores: {
            sectores?: components["schemas"]["SectorRespuesta"][];
            /**
             * Format: date-time
             * @description Instante en que el servidor genero esta respuesta (UTC)
             */
            generadoEn?: string;
        };
        /** @description Sector de Cartagena con el estado conocido de su servicio de agua */
        SectorRespuesta: {
            /**
             * @description Identificador estable del sector
             * @example bocagrande
             */
            id?: string;
            /**
             * @description Nombre del barrio segun el GeoJSON oficial
             * @example BOCAGRANDE
             */
            nombre?: string;
            /**
             * Format: int32
             * @description Habitantes según el censo. **Nulo cuando el barrio no tiene dato censal** (27 de los 211): no es 0,
             *     y no debe mostrarse como «0 habitantes».
             * @example 12000
             */
            poblacion?: number | null;
            /**
             * @description Estado conocido del servicio. **Nulo cuando no hay dato verificado**: no se asume
             *     CON_SERVICIO por omision, porque publicar servicio normal sin verificarlo es el
             *     falso positivo que el proyecto evita (ADR-014). Presentarlo como "sin datos".
             * @enum {string|null}
             */
            estado?: "CON_SERVICIO" | "SIN_SERVICIO" | "PRESION_BAJA" | "CORTE_PROGRAMADO" | null;
            /**
             * Format: date-time
             * @description Cuando se registro ese estado. Nulo si el sector no tiene estado.
             */
            actualizadoEn?: string | null;
            /**
             * Format: date-time
             * @description Última vez que una fuente con autoridad (consenso de vecinos, corte del veedor o boletín
             *     aprobado) sostuvo ese estado, haya cambiado o no (ADR-073). Nunca anterior a
             *     `actualizadoEn`. Nulo si el sector no tiene estado. Confirmar sin cambiar no emite
             *     evento SSE: el valor se renueva al volver a pedir la lista.
             */
            verificadoEn?: string | null;
        };
        /** @description Sector con su cantidad de cortes registrados */
        EstadisticaSectorRespuesta: {
            sectorId?: string;
            nombre?: string;
            /** Format: int32 */
            cantidadCortes?: number;
        };
        /** @description Estadísticas públicas globales (M7, RF023) */
        EstadisticasRespuesta: {
            sectoresMasAfectados?: components["schemas"]["EstadisticaSectorRespuesta"][];
            cortesPorDiaDeSemana?: {
                [key: string]: number;
            };
            /** Format: double */
            duracionPromedioHoras?: number;
        };
        /**
         * @description Índice de Cumplimiento (RF020-RF022): comparación explícita entre duración prometida y
         *     real, nunca un porcentaje aislado.
         */
        IndiceCumplimientoRespuesta: {
            /** @description Nulo cuando el índice es por corte o global, no por sector */
            sectorId?: string;
            /** Format: int64 */
            duracionPrometidaSegundos?: number;
            /** Format: int64 */
            duracionRealSegundos?: number;
            /**
             * Format: int64
             * @description duracionReal - duracionPrometida. Negativa si terminó antes de lo prometido
             */
            desviacionSegundos?: number;
            /**
             * Format: double
             * @description Capado en 100 cuando el corte termina antes o a tiempo
             */
            porcentajeCumplimiento?: number;
        };
        /** @description Un mes de la evolución del Índice de Cumplimiento (RF024) */
        PuntoSerieRespuesta: {
            /**
             * @description Mes en hora de Cartagena, ISO 8601
             * @example 2026-08
             */
            periodo?: string;
            /** Format: int64 */
            duracionPrometidaSegundos?: number;
            /** Format: int64 */
            duracionRealSegundos?: number;
            /**
             * Format: int64
             * @description duracionReal - duracionPrometida. Negativa si terminaron antes de lo prometido
             */
            desviacionSegundos?: number;
            /**
             * Format: double
             * @description Capado en 100 cuando los cortes terminan antes o a tiempo
             */
            porcentajeCumplimiento?: number;
            /**
             * Format: int32
             * @description Cortes cerrados sobre los que se calculó el mes. Un 40% sobre un solo corte y uno
             *     sobre veinte no significan lo mismo.
             */
            cantidadCortes?: number;
        };
        /** @description Evento de la bitácora pública, de solo anexado (RF026-RF028) */
        EventoBitacoraRespuesta: {
            id?: string;
            /** @description CORTE_ANUNCIADO, CORTE_CONFIRMADO_POR_CIUDADANOS, CORTE_RESTABLECIDO o CORTE_DETECTADO_POR_INGESTA */
            tipo?: string;
            /** @description Nulo si el evento no está atado a un sector */
            sectorId?: string;
            /** @description Nulo si el evento no está atado a un corte oficial (p. ej. consenso ciudadano) */
            corteId?: string;
            /** Format: date-time */
            timestamp?: string;
            descripcion?: string;
            /** @description Estado del servicio que afirma el evento: CON_SERVICIO, SIN_SERVICIO, PRESION_BAJA o CORTE_PROGRAMADO. Nulo si el evento no habla del servicio — presentarlo entonces como informativo, sin color de estado. */
            estado?: string;
            /** @description Boletín o nota que respalda el evento. Nulo si la fuente no lo trae. */
            urlOriginal?: string;
            /** @description Portada del boletín. Nula si la fuente no la trae. */
            imagenUrl?: string;
            /**
             * Format: int32
             * @description RF011 — cuántos reportes ciudadanos sostuvieron el cambio, en los eventos de consenso; 0 en los demás. Los ids no viajan en el listado (pesaban cientos de KB por página): se piden con GET /api/bitacora/{id}/sustento.
             */
            cantidadReportesSustento?: number;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    reenviarInvitacion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Invitación reenviada */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No existe la cuenta */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description La cuenta ya aceptó la invitación (no está en estado INVITADA) */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    invitar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudInvitacion"];
            };
        };
        responses: {
            /** @description Invitacion enviada */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Ya existe una cuenta con ese correo */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
        };
    };
    iniciarSesion: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CredencialVeedor"];
            };
        };
        responses: {
            /** @description Credencial correcta, token emitido */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SesionVeedor"];
                };
            };
            /** @description Credencial incorrecta, o falta el segundo factor */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SesionVeedor"];
                };
            };
            /** @description La cuenta existe pero no esta habilitada para entrar */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SesionVeedor"];
                };
            };
            /** @description Cuenta bloqueada por intentos fallidos */
            423: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SesionVeedor"];
                };
            };
            /** @description Demasiados intentos desde esta IP */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SesionVeedor"];
                };
            };
        };
    };
    cerrar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description No Content */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    confirmar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudCodigo"];
            };
        };
        responses: {
            /** @description Segundo factor activo; sesion nueva emitida */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SesionVeedor"];
                };
            };
            /** @description El codigo no coincide */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SesionVeedor"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No hay un alta en curso */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SesionVeedor"];
                };
            };
        };
    };
    desactivar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudCodigo"];
            };
        };
        responses: {
            /** @description Segundo factor desactivado */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description El codigo no coincide */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description El rol ADMIN exige segundo factor */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    iniciar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["SolicitudCodigo"];
            };
        };
        responses: {
            /** @description Secreto generado, pendiente de confirmar */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AltaSegundoFactorRespuesta"];
                };
            };
            /** @description El codigo actual no coincide */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AltaSegundoFactorRespuesta"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Ya tiene segundo factor y no envio el codigo actual */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AltaSegundoFactorRespuesta"];
                };
            };
        };
    };
    cambiarClave: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudCambioClave"];
            };
        };
        responses: {
            /** @description Clave cambiada; todas las sesiones cerradas */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description La clave actual no es correcta, la nueva no cumple la política (12 a 128 caracteres) o es igual a la actual */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Cuenta bloqueada por intentos fallidos */
            423: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    listarPorSector: {
        parameters: {
            query: {
                sectorId: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CorteRespuesta"][];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    registrar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudCorte"];
            };
        };
        responses: {
            /** @description Corte registrado */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CorteRespuesta"];
                };
            };
            /** @description Datos inválidos o algún sector no existe */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    suscribirse: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudSuscripcion"];
            };
        };
        responses: {
            /** @description Suscripción creada, correo de confirmación en camino */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SuscripcionRespuesta"];
                };
            };
            /** @description Correo inválido o algún sector no existe */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    pantallaConfirmar: {
        parameters: {
            query: {
                token: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Página con el botón */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/html": string;
                };
            };
        };
    };
    confirmar_1: {
        parameters: {
            query: {
                token: string;
            };
            header?: {
                Accept?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Suscripción confirmada */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": Record<string, never>;
                    "text/html": Record<string, never>;
                };
            };
            /** @description Token inválido, inexistente o de una suscripción ya cancelada */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    pantallaCancelar: {
        parameters: {
            query: {
                token: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Página con el botón */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/html": string;
                };
            };
        };
    };
    cancelar: {
        parameters: {
            query: {
                token: string;
            };
            header?: {
                Accept?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Suscripción cancelada */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": Record<string, never>;
                    "text/html": Record<string, never>;
                };
            };
            /** @description Token inválido o inexistente */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    registrar_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudReporte"];
            };
        };
        responses: {
            /** @description Reporte registrado */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteRespuesta"];
                };
            };
            /** @description Sector inexistente, tipo inválido, huella fuera de 32-128 caracteres, coordenada fuera de Cartagena o sin sector ni coordenada */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description El dispositivo superó el límite de reportes para este sector */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    agregarEvidencia: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    /** Format: binary */
                    foto: string;
                };
            };
        };
        responses: {
            /** @description Evidencia agregada */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteRespuesta"];
                };
            };
            /** @description Error en la solicitud */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteRespuesta"];
                };
            };
            /** @description Reporte no encontrado */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteRespuesta"];
                };
            };
        };
    };
    confirmar_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudConfirmar"];
            };
        };
        responses: {
            /** @description Reporte confirmado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteRespuesta"];
                };
            };
            /** @description Error en la solicitud */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteRespuesta"];
                };
            };
            /** @description Reporte no encontrado */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteRespuesta"];
                };
            };
        };
    };
    reportarPresion: {
        parameters: {
            query?: never;
            header?: {
                "X-IoT-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["IotPresionRequest"];
            };
        };
        responses: {
            /** @description Lectura recibida (haya generado reporte o no) */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Falta el sensor, el sector no existe o la coordenada es inválida */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description X-IoT-Key ausente o incorrecta */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description El servidor no tiene configurada la clave de sensores */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    verificarCorreo: {
        parameters: {
            query: {
                token: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Correo confirmado */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Enlace invalido, vencido o ya usado */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    reenviarVerificacion: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudReenvioVerificacion"];
            };
        };
        responses: {
            /** @description Si había algo que reenviar, el correo va en camino */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Correo mal formado */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    pedirRestablecimiento: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudRestablecer"];
            };
        };
        responses: {
            /** @description Accepted */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    registrarse: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudRegistro"];
            };
        };
        responses: {
            /** @description Solicitud recibida; revisa tu correo */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Correo mal formado o clave que no cumple la politica */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    aceptar: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudFijarClave"];
            };
        };
        responses: {
            /** @description Cuenta activa; ya puedes iniciar sesion */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Enlace invalido o clave que no cumple la politica */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    pantallaVerificar: {
        parameters: {
            query: {
                token: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/html": string;
                };
            };
        };
    };
    verificarCorreo_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/x-www-form-urlencoded": {
                    token: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/html": string;
                };
            };
        };
    };
    pantallaRestablecer: {
        parameters: {
            query: {
                token: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/html": string;
                };
            };
        };
    };
    restablecerClave: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/x-www-form-urlencoded": {
                    token: string;
                    clave: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/html": string;
                };
            };
        };
    };
    pantallaInvitacion: {
        parameters: {
            query: {
                token: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/html": string;
                };
            };
        };
    };
    aceptarInvitacion: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/x-www-form-urlencoded": {
                    token: string;
                    clave: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/html": string;
                };
            };
        };
    };
    fijarClave: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudFijarClave"];
            };
        };
        responses: {
            /** @description Clave cambiada */
            204: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Enlace invalido, vencido o ya usado */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    suspender: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Cuenta suspendida */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Es el unico ADMIN activo, o el ADMIN se administra a si mismo */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
        };
    };
    rechazar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    reactivar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    cambiarPermisos: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudPermisos"];
            };
        };
        responses: {
            /** @description Permisos actualizados */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description Dejaria al sistema sin ningun ADMIN activo */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
        };
    };
    aprobar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudPermisos"];
            };
        };
        responses: {
            /** @description Cuenta activa */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La cuenta no esta esperando aprobacion, o el ADMIN se administra a si mismo */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
        };
    };
    descartar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Reporte descartado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteModeracionRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description El reporte no existe */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    aprobar_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Reporte aprobado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteModeracionRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description El reporte no existe */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    descartar_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Propuesta descartada */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PropuestaIngestaRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La propuesta no existe */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    aprobar_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Propuesta aprobada y estado aplicado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PropuestaIngestaRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La propuesta no existe */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description El sector de la propuesta ya no existe */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    cerrar_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SolicitudCierreCorte"];
            };
        };
        responses: {
            /** @description Corte cerrado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CorteRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description El corte no existe */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description El corte ya estaba cerrado */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    sinProteger: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    protegida: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": string;
                };
            };
        };
    };
    yo: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    listar: {
        parameters: {
            query?: {
                estado?: string;
                pagina?: number;
                tamano?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UsuarioRespuesta"][];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    listarPendientes: {
        parameters: {
            query?: {
                pagina?: number;
                tamano?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ReporteModeracionRespuesta"][];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    salud: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Estado generado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SaludColectorRespuesta"][];
                };
            };
            /** @description Falta el token del veedor */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SaludColectorRespuesta"][];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    listarPendientes_1: {
        parameters: {
            query?: {
                pagina?: number;
                tamano?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Listado generado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PropuestaIngestaRespuesta"][];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    fallidos: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Listado generado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DocumentoFallidoRespuesta"][];
                };
            };
            /** @description Falta el token del veedor */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DocumentoFallidoRespuesta"][];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    consultar: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Corte encontrado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CorteRespuesta"];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description No existe un corte con ese id */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    auditoria: {
        parameters: {
            query?: {
                pagina?: number;
                tamano?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EventoAuditoriaRespuesta"][];
                };
            };
            /** @description Sin sesión, token inválido, caducado o revocado */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description La sesión no tiene el permiso que exige esta operación */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    getRequests: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Listado generado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Open311Response"][];
                };
            };
        };
    };
    listarSectores: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Listado generado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RespuestaSectores"];
                };
            };
        };
    };
    listar_1: {
        parameters: {
            query?: {
                pagina?: number;
                tamano?: number;
            };
            header?: never;
            path: {
                sectorId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Cortes de la página pedida */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CorteRespuesta"][];
                };
            };
            /** @description No existe el sector */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CorteRespuesta"][];
                };
            };
        };
    };
    consultarSector: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Sector encontrado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SectorRespuesta"];
                };
            };
            /** @description No existe un sector con ese id */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    streamSectores: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Tope de conexiones en vivo alcanzado; reintentar tras Retry-After */
            429: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    geometria: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Geometrías */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/geo+json": unknown;
                };
            };
        };
    };
    globales: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EstadisticasRespuesta"];
                };
            };
        };
    };
    exportarCsv: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description CSV generado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/csv": string;
                };
            };
        };
    };
    global: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Índice calculado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IndiceCumplimientoRespuesta"];
                };
            };
            /** @description Todavía no hay cortes cerrados */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    serie: {
        parameters: {
            query?: {
                sectorId?: string;
                desde?: string;
                hasta?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Serie generada */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PuntoSerieRespuesta"][];
                };
            };
        };
    };
    serieCsv: {
        parameters: {
            query?: {
                sectorId?: string;
                desde?: string;
                hasta?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description CSV generado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/csv": string;
                };
            };
        };
    };
    porSector: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sectorId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Índice calculado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IndiceCumplimientoRespuesta"];
                };
            };
            /** @description El sector no tiene cortes cerrados todavía */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    porCorte: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                corteId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Índice calculado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IndiceCumplimientoRespuesta"];
                };
            };
            /** @description El corte no existe */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
            /** @description El corte todavía no está cerrado */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    listar_2: {
        parameters: {
            query?: {
                pagina?: number;
                tamano?: number;
                sectorId?: string;
                tipo?: "CORTE_ANUNCIADO" | "CORTE_CONFIRMADO_POR_CIUDADANOS" | "CORTE_RESTABLECIDO" | "CORTE_DETECTADO_POR_INGESTA";
                desde?: string;
                hasta?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Listado generado */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["EventoBitacoraRespuesta"][];
                };
            };
            /** @description Tipo de evento desconocido, fecha mal formada o rango invertido */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["ProblemDetail"];
                };
            };
        };
    };
    sustento: {
        parameters: {
            query?: {
                pagina?: number;
                tamano?: number;
            };
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Ids de la página pedida (vacía si se pasa del final) */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": string[];
                };
            };
            /** @description No existe el evento */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": string[];
                };
            };
        };
    };
}
