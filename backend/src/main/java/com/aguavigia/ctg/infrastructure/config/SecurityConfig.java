package com.aguavigia.ctg.infrastructure.config;

import com.aguavigia.ctg.domain.port.out.RevocacionSesionPort;
import com.aguavigia.ctg.infrastructure.security.JwtAuthenticationFilter;
import com.aguavigia.ctg.infrastructure.security.JwtProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RF019: el panel del veedor exige token. Todo lo que no esté en la lista de rutas públicas de
 * abajo se deniega por defecto (`denyAll`): un endpoint nuevo fuera de /api/veedor/** no queda
 * público por descuido, hay que declararlo aquí a propósito.
 *
 * Esta cadena decide *si hace falta una sesion*; qué puede hacer esa sesión lo deciden los
 * `@PreAuthorize` de cada controlador contra un Permiso concreto (@EnableMethodSecurity). La
 * anotación viaja pegada al método que protege; la lista de rutas públicas solo abre lo que es
 * público de verdad.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private static final String BASE_TIPO = "https://aguavigia.example/errores/";

    /** Lectura y reporte ciudadano, avisos, telemetría y salud: lo que existe sin sesión. */
    private static final String[] RUTAS_PUBLICAS = {
            "/api/sectores/**", "/api/reportes/**", "/api/cumplimiento/**", "/api/estadisticas/**",
            "/api/bitacora/**", "/api/suscripciones/**", "/api/iot/**", "/api/v2/**",
            "/actuator/health", "/actuator/health/**",
            // Páginas de error del contenedor: sin esto un 404 o un 500 saldrían como 401.
            "/error",
            // Solo existen en los perfiles que no las desactivan (application-prod.yml).
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"
    };

    private static final List<RequestMatcher> DOCUMENTACION_INTERACTIVA = List.of(
            new AntPathRequestMatcher("/swagger-ui.html"), new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/v3/api-docs/**"));

    /**
     * La API responde JSON y unas pocas páginas HTML sencillas (enlaces de cuenta): sin scripts,
     * sin recursos externos, sin poder ser embebidas en un marco. Swagger UI queda fuera porque
     * necesita sus propios scripts.
     */
    private static final String POLITICA_CSP = "default-src 'none'; style-src 'unsafe-inline'; "
            + "form-action 'self'; base-uri 'none'; frame-ancestors 'none'";

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Sin esto el preflight nunca llegaba a resolverse: Spring Security responde 403 a un
     * OPTIONS sin credenciales antes de que MVC lo vea, asi que un frontend en otro origen no
     * podia ni siquiera preguntar.
     *
     * El bean existe siempre — devolver `null` desde un @Bean deja un NullBean que la cadena de
     * seguridad no sabe resolver — pero sin origenes declarados no registra ningun mapeo, con lo
     * que no se emite ninguna cabecera CORS y el comportamiento queda igual que antes.
     */
    @Bean
    public CorsConfigurationSource fuenteDeConfiguracionCors(CorsProperties propiedades) {
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        if (!propiedades.habilitado()) {
            return fuente;
        }

        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(propiedades.origenesPermitidos());
        configuracion.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-IoT-Key"));
        // El token del veedor viaja en la cabecera Authorization, no en cookie: no hace falta
        // permitir credenciales, y no permitirlas evita el combo prohibido con origenes amplios.
        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(3600L);

        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http,
                                                 JwtProvider jwtProvider,
                                                 RevocacionSesionPort revocacion,
                                                 // Con @Qualifier porque Spring MVC registra otro
                                                 // CorsConfigurationSource propio (el
                                                 // mvcHandlerMappingIntrospector) y por tipo la
                                                 // inyeccion queda ambigua.
                                                 @Qualifier("fuenteDeConfiguracionCors")
                                                 CorsConfigurationSource fuenteCors) throws Exception {
        http
                // Se pasa la fuente explicitamente en vez de Customizer.withDefaults(): ese
                // atajo solo recoge un bean que se llame literalmente "corsConfigurationSource",
                // asi que con el nombre en espanol la configuracion se ignoraba en silencio y
                // el preflight seguia respondiendo 403.
                .cors(cors -> cors.configurationSource(fuenteCors))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Alta y recuperacion: por definicion nadie tiene sesion todavia cuando
                        // los llama. Su freno es el limite por IP (application.yml) y el hecho de
                        // que ninguno concede acceso por si solo — hace falta aprobacion o un
                        // enlace que solo llega al correo del titular.
                        .requestMatchers(HttpMethod.POST, "/api/veedor/sesion").permitAll()
                        .requestMatchers("/api/cuentas/**").permitAll()
                        // Ingreso de vecinos: igual que el del panel, nadie tiene sesion todavia.
                        // El resto de /api/vecino/** exige sesion y, ademas, GESTIONAR_PERFIL_PROPIO.
                        .requestMatchers(HttpMethod.POST, "/api/vecino/sesion").permitAll()
                        .requestMatchers("/api/vecino/**").authenticated()
                        .requestMatchers("/api/veedor/**").authenticated()
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .anyRequest().denyAll())
                .headers(cabeceras -> cabeceras
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(new OrRequestMatcher(DOCUMENTACION_INTERACTIVA)),
                                new StaticHeadersWriter("Content-Security-Policy", POLITICA_CSP))))
                .exceptionHandling(manejo -> manejo
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED,
                                    "Se requiere autenticacion para acceder a este recurso.");
                            problema.setTitle("No autenticado");
                            problema.setType(URI.create(BASE_TIPO + "no-autenticado"));
                            problema.setInstance(URI.create(request.getRequestURI()));
                            response.getWriter().write(objectMapper.writeValueAsString(problema));
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                                    HttpStatus.FORBIDDEN,
                                    "No tienes permisos suficientes para realizar esta accion.");
                            problema.setTitle("Acceso denegado");
                            problema.setType(URI.create(BASE_TIPO + "acceso-denegado"));
                            problema.setInstance(URI.create(request.getRequestURI()));
                            response.getWriter().write(objectMapper.writeValueAsString(problema));
                        }))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, revocacion),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
