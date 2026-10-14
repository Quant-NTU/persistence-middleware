
package sg.com.quantai.middleware.configs

import jakarta.servlet.DispatcherType
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val casbinFilter: CasbinFilter
) {

    // Prevent CasbinFilter from being automatically registered
    // as a servlet filter outside Spring Security.
    // It is added explicitly to the Spring Security filter chain below.
    @Bean
    fun casbinFilterRegistration(): FilterRegistrationBean<CasbinFilter> =
        FilterRegistrationBean<CasbinFilter>().apply {
            filter = casbinFilter
            isEnabled = false
        }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {

                // FIX: Allow Spring Boot to return normal error responses.
                // Without this, a 400 Bad Request can become a misleading
                // 401 Unauthorized when Spring forwards to /error.
                it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                // Allow CORS preflight requests.
                it.requestMatchers(
                    HttpMethod.OPTIONS,
                    "/**"
                ).permitAll()

                // Public endpoints: registration, login and 2FA verification.
                it.requestMatchers(
                    HttpMethod.POST,
                    "/users",
                    "/users/signin",
                    "/users/verify-2fa",
                    "/users/generate-token",
                    "/users/authenticate-token"
                ).permitAll()

                // Password reset is authorized by its reset token.
                it.requestMatchers(
                    HttpMethod.PUT,
                    "/users/reset-password/*"
                ).permitAll()

                // All other endpoints require authentication.
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.jwt { }
            }

        // Run Casbin RBAC after JWT authentication.
        http.addFilterAfter(
            casbinFilter,
            BearerTokenAuthenticationFilter::class.java
        )

        return http.build()
    }
}
