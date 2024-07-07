package monolith.spring.mail.config

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Component
private class EmailProperties {
    @Value("\${spring.mail.host}")
    lateinit var host: String 

    @Value("\${spring.mail.port}")
    lateinit var port: String 

    @Value("\${spring.mail.username}")
    lateinit var user: String 

    @Value("\${spring.mail.password}")
    lateinit var password: String 
}

@Configuration
public class EmailConfig {
    @Autowired
    lateinit private var emailProperties: EmailProperties

    @Bean
    fun getJavaMailSender(): JavaMailSender {
        val mailSender: JavaMailSenderImpl = JavaMailSenderImpl();
        // Set up Gmail config
        mailSender.setHost(emailProperties.host);
        mailSender.setPort(emailProperties.port.toInt());

        // Set up email config (using udeesa email)
        mailSender.setUsername(emailProperties.user);
        mailSender.setPassword(emailProperties.password);

        val props: Properties = mailSender.getJavaMailProperties();
        //props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        //props.put("mail.debug", debug);
        return mailSender;
    }
}