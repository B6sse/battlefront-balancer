package no.battlefront.balancer.config

import no.battlefront.balancer.ratelimit.LoginRateLimitFilter
import no.battlefront.balancer.ratelimit.LoginRateLimitStore
import no.battlefront.balancer.security.HostTokenAuthenticationFilter
import no.battlefront.balancer.service.HostTokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * Spring Security configuration for the API.
 *
 * Configures session-based authentication (login via POST /api/login). CSRF is disabled because
 * the session cookie uses SameSite=Strict, which prevents cross-site request forgery without
 * needing CSRF tokens. Public endpoints (health, players list, randomizer, last-match, login,
 * logout) use **permitAll**; [GET /api/me] requires **authenticated**. Roles, from least to most rights:
 * **supervisor** (submit matches), **editor** (supervisor + player CRUD), **admin** (everything, including
 * users, seasons, randomizer weights and host tokens). Form login, HTTP Basic and the default logout filter
 * are disabled in favour of custom [AuthController][no.battlefront.balancer.controller.AuthController] endpoints.
 * Login rate limiting is applied before authentication.
 *
 * Auric hosts authenticate with `Authorization: Bearer <token>` instead of a session. Those requests are handled
 * by a separate, stateless chain ([hostTokenFilterChain]) that only allows the host endpoints.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    /**
     * Provides a BCrypt password encoder for hashing and verifying user passwords.
     *
     * @return the [PasswordEncoder] bean used by the application
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * In-memory store for login rate limits (per IP). Configure via app.rate-limit.login.max-per-minute.
     *
     * @param maxPerMinute maximum login attempts per IP per minute
     * @return the [LoginRateLimitStore] bean
     */
    @Bean
    fun loginRateLimitStore(
        @Value("\${app.rate-limit.login.max-per-minute:10}") maxPerMinute: Int,
    ): LoginRateLimitStore = LoginRateLimitStore(maxPerMinute)

    /**
     * Exposes the [AuthenticationManager] used by the login endpoint to authenticate
     * username and password.
     *
     * @param config the authentication configuration supplied by Spring
     * @return the [AuthenticationManager] for programmatic authentication
     */
    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager = config.authenticationManager

    /**
     * Filter chain for requests with `Authorization: Bearer`, i.e. Auric hosts using a host token.
     * Stateless, so no session is created per call. [HostTokenAuthenticationFilter] rejects invalid tokens with 401;
     * valid ones get **ROLE_host**, which may call only the host endpoints below. Everything else is denied.
     *
     * @param http the [HttpSecurity] to configure
     * @param hostTokenService verifies tokens
     * @return the configured [SecurityFilterChain]
     */
    @Bean
    @Order(1)
    fun hostTokenFilterChain(
        http: HttpSecurity,
        hostTokenService: HostTokenService,
    ): SecurityFilterChain {
        http
            .securityMatcher(
                RequestMatcher { request ->
                    request.getHeader(HttpHeaders.AUTHORIZATION)?.startsWith("Bearer ", ignoreCase = true) == true
                },
            ).addFilterBefore(HostTokenAuthenticationFilter(hostTokenService), AnonymousAuthenticationFilter::class.java)
            .csrf { it.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }.exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.sendError(401)
                }
            }.authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/players", "/api/players/by-persona")
                    .hasAuthority(HostTokenAuthenticationFilter.ROLE_HOST)
                    .requestMatchers(HttpMethod.POST, "/api/balance", "/api/player-requests", "/api/matches/raw")
                    .hasAuthority(HostTokenAuthenticationFilter.ROLE_HOST)
                    .anyRequest()
                    .denyAll()
            }.formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
        return http.build()
    }

    /**
     * Defines the security filter chain: which paths are public, which require authentication,
     * and which require specific authorities. Session creation policy is [SessionCreationPolicy.IF_REQUIRED].
     * Any request not explicitly permitted or requiring only authentication/authorities is denied.
     * [LoginRateLimitFilter] runs early to rate-limit POST /api/login before authentication.
     *
     * @param http the [HttpSecurity] to configure
     * @return the configured [SecurityFilterChain]
     */
    @Bean
    @Order(2)
    fun securityFilterChain(
        http: HttpSecurity,
        loginRateLimitFilter: LoginRateLimitFilter,
    ): SecurityFilterChain {
        http
            .addFilterBefore(loginRateLimitFilter, SecurityContextHolderFilter::class.java)
            .csrf { it.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            }.exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.sendError(401)
                }
            }.authorizeHttpRequests { auth ->
                auth
                    // Spring forwards errors (e.g. malformed JSON → 400) to /error; without this they surface as 401.
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/health",
                        "/api/players",
                        "/api/players/by-persona",
                        "/api/players/*/matches",
                        "/api/randomizer",
                        "/api/last-match",
                        "/api/seasons",
                        "/api/matches",
                        "/api/matches/*",
                    ).permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/login",
                        "/api/login/2fa",
                        "/api/login/2fa/setup",
                        "/api/login/2fa/setup/confirm",
                        "/api/logout",
                        "/api/balance",
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/admin/**")
                    .hasAuthority("ROLE_admin")
                    .requestMatchers(HttpMethod.POST, "/api/admin/**")
                    .hasAuthority("ROLE_admin")
                    .requestMatchers(HttpMethod.PUT, "/api/admin/**")
                    .hasAuthority("ROLE_admin")
                    .requestMatchers(HttpMethod.DELETE, "/api/admin/users/*", "/api/admin/host-tokens/*")
                    .hasAuthority("ROLE_admin")
                    .requestMatchers(HttpMethod.GET, "/api/randomizer/weights")
                    .hasAuthority("ROLE_admin")
                    .requestMatchers(HttpMethod.PUT, "/api/randomizer/weights")
                    .hasAuthority("ROLE_admin")
                    .requestMatchers(HttpMethod.GET, "/api/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/matches", "/api/matches/raw", "/api/randomizer")
                    .hasAnyAuthority("ROLE_admin", "ROLE_editor", "ROLE_supervisor")
                    .requestMatchers(HttpMethod.POST, "/api/players")
                    .hasAnyAuthority("ROLE_admin", "ROLE_editor")
                    .requestMatchers(HttpMethod.PUT, "/api/players/*")
                    .hasAnyAuthority("ROLE_admin", "ROLE_editor")
                    .requestMatchers(HttpMethod.DELETE, "/api/players/*")
                    .hasAnyAuthority("ROLE_admin", "ROLE_editor")
                    .requestMatchers(HttpMethod.GET, "/api/player-requests")
                    .hasAnyAuthority("ROLE_admin", "ROLE_editor")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/player-requests/*/approve",
                        "/api/player-requests/*/link",
                        "/api/player-requests/*/reject",
                    ).hasAnyAuthority("ROLE_admin", "ROLE_editor")
                    .anyRequest()
                    .denyAll()
            }.formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
        return http.build()
    }
}
