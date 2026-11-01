package sg.com.quantai.middleware.security

import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class TotpService {

    fun verifyCode(base32Secret: String, submittedCode: String): Boolean {
        if (!submittedCode.matches(Regex("^[0-9]{6}$"))) {
            return false
        }

        val secret = try {
            decodeBase32(base32Secret)
        } catch (ex: IllegalArgumentException) {
            return false
        }

        if (secret.isEmpty()) return false

        val currentCounter = System.currentTimeMillis() / 1000 / 30

        // Allow one 30-second window on either side for clock drift.
        for (offset in -1L..1L) {
            val expected = generateCode(secret, currentCounter + offset)

            if (MessageDigest.isEqual(
                    expected.toByteArray(Charsets.US_ASCII),
                    submittedCode.toByteArray(Charsets.US_ASCII)
                )
            ) {
                return true
            }
        }

        return false
    }

    private fun generateCode(secret: ByteArray, counter: Long): String {
        val message = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))

        val hash = mac.doFinal(message)
        val offset = hash.last().toInt() and 0x0F

        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 1_000_000

        return otp.toString().padStart(6, '0')
    }

    private fun decodeBase32(value: String): ByteArray {
        val normalized = value
            .uppercase()
            .replace(" ", "")
            .trimEnd('=')

        require(normalized.isNotEmpty())

        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val output = ArrayList<Byte>()

        var buffer = 0
        var bitsLeft = 0

        for (character in normalized) {
            val digit = alphabet.indexOf(character)
            require(digit >= 0) { "Invalid Base32 secret" }

            buffer = (buffer shl 5) or digit
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }

        return output.toByteArray()
    }
}
