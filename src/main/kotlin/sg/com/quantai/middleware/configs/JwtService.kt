package sg.com.quantai.middleware.configs

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class JwtService(
    private val jwtEncoder: JwtEncoder,
    @Value("\${app.jwt.issuer}") private val issuer: String,
) {
    fun generateToken(uid: String): String {
        val now = Instant.now()

        val claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(uid)
            .issuedAt(now)
            .expiresAt(now.plus(30, ChronoUnit.MINUTES))
            .claim("uid", uid)
            .build()

        val headers = JwsHeader.with(MacAlgorithm.HS256).build()

        return jwtEncoder.encode(
            JwtEncoderParameters.from(headers, claims)
        ).tokenValue
    }
}
