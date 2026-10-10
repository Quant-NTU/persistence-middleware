
package sg.com.quantai.middleware.configs

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

    // Prevent Spring Boot from automatically registering CasbinFilter
    // as a standalone servlet filter.
    //
    // CasbinFilter will run only within the Spring Security filter chain,
    // after JWT authentication.
    @Bean
    fun casbinFilterRegistration(): FilterRegistrationBean<CasbinFilter> {
        return FilterRegistrationBean<CasbinFilter>().apply {
            filter = casbinFilter
            isEnabled = false
        }
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
                it.requestMatchers(
                    HttpMethod.OPTIONS,
                    "/**"
                ).permitAll()

                // Public registration endpoint
		it.requestMatchers(
   		   HttpMethod.POST,
    		   "/users"
 		   ).permitAll()

		// Public authentication endpoints
		   it.requestMatchers(
    		   HttpMethod.POST,
    		   "/users/signin"
		).permitAll()

                it.requestMatchers(
                    HttpMethod.POST,
                    "/users/verify-2fa"
                ).permitAll()

                // Public password-reset endpoints
                it.requestMatchers(
                    HttpMethod.POST,
                    "/users/generate-token"
                ).permitAll()

                it.requestMatchers(
                    HttpMethod.POST,
                    "/users/authenticate-token"
                ).permitAll()

                // All other endpoints require a verified JWT
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.jwt { }
            }

        // Run Casbin after JWT authentication.
        http.addFilterAfter(
            casbinFilter,
            BearerTokenAuthenticationFilter::class.java
        )

        return http.build()
    }
}
