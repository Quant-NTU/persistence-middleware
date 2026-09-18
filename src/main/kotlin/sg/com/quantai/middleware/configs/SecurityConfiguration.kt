package sg.com.quantai.middleware.configs

import org.springframework.web.cors.CorsUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.http.HttpMethod
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val casbinFilter: CasbinFilter
) {

    @Throws(Exception::class)
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors().and()
            .csrf().disable()
            .httpBasic().disable()
            .formLogin().disable()
            .authorizeRequests()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
            .anyRequest().permitAll()

        http.addFilterBefore(casbinFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}