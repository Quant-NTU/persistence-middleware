package sg.com.quantai.middleware.mailsender.config

import jakarta.mail.MessagingException
import jakarta.mail.internet.MimeMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import jakarta.mail.internet.InternetAddress
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

@Component
private class AppProperties {
    @Value("\${spring.default.link}")
    lateinit var link: String

    @Value("\${spring.mail.username}")
    lateinit var user: String
}

@Service
class EmailServiceImpl (private val javaMailSender: JavaMailSender) {
    @Autowired
    lateinit private var appProperties: AppProperties

    fun sendResetPasswordEmail(name: String, email: String, token: String?)
    {

        // Try block to check for exceptions
        try {
            val message: MimeMessage = javaMailSender.createMimeMessage();
            val helper: MimeMessageHelper = MimeMessageHelper(message, true)

            val resetpassword_link = appProperties.link
            var content = "$name have requested to reset your password. Your validation token is $token \n\n Please click the link below within 1 hour to reset password: \n"
            content += resetpassword_link

            helper.setFrom(InternetAddress(appProperties.user));
            helper.setTo(InternetAddress(email));
            helper.setSubject("Request for Password Reset");
            helper.setText(content);

            javaMailSender.send(message);

            println("Success")
        }
        catch (e: Exception ) {
            //return "Error while Sending Mail";
            println("Fail with " + e)
        }
    }

}