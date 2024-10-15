package sg.com.quantai.middleware

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MiddlewareApplication

fun main(args: Array<String>) {
	runApplication<MiddlewareApplication>(*args)
}