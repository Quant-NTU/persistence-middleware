package sg.com.quantai.middleware

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
// Added for swagger
import org.springframework.web.servlet.config.annotation.EnableWebMvc; 
import springfox.documentation.swagger2.annotations.EnableSwagger2;


//Added for swagger #
@EnableSwagger2
@EnableWebMvc

@SpringBootApplication
class MiddlewareApplication

fun main(args: Array<String>) {
	runApplication<MiddlewareApplication>(*args)
}