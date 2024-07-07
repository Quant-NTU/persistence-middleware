package monolith.config

import org.springframework.web.cors.CorsUtils
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.http.HttpMethod

@Configuration

class SecurityConfiguration : WebSecurityConfigurerAdapter() {
    override fun configure(http: HttpSecurity) {
        http
            .cors().and()
            .csrf().disable()
            .authorizeRequests()
            .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
            .anyRequest().permitAll()
    }
}