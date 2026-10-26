package sg.com.quantai.middleware.configs

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import javax.crypto.spec.SecretKeySpec

@Configuration
class JwtConfig(
    @Value("\${app.jwt.secret}") private val jwtSecret: String,
    @Value("\${app.jwt.issuer}") private val jwtIssuer: String,
) {
    private fun signingKey(): SecretKeySpec {
        val secretBytes = jwtSecret.toByteArray(Charsets.UTF_8)

        require(secretBytes.size >= 32) {
            "JWT secret must contain at least 32 bytes"
        }

        return SecretKeySpec(secretBytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(): JwtEncoder {
        val jwkSource = ImmutableSecret<SecurityContext>(signingKey())
        return NimbusJwtEncoder(jwkSource)
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder
            .withSecretKey(signingKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build()

        decoder.setJwtValidator(
            JwtValidators.createDefaultWithIssuer(jwtIssuer)
        )

        return decoder
    }
}
