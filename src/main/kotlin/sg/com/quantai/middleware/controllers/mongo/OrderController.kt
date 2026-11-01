package sg.com.quantai.middleware.controllers.mongo

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sg.com.quantai.middleware.data.mongo.Order
import sg.com.quantai.middleware.data.mongo.enums.OrderStatus
import sg.com.quantai.middleware.requests.CreateOrderRequest
import sg.com.quantai.middleware.requests.OrderFillRequest
import sg.com.quantai.middleware.services.OrderService

@RestController
@RequestMapping("/orders")
class OrderController(private val orderService: OrderService) {

    @PostMapping("/{user_id}")
    fun createOrder(
        @PathVariable("user_id") userId: String,
        @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<Any> {
        val order = orderService.createOrder(userId, request)
        return if (order.status == OrderStatus.REJECTED) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                    mapOf(
                        "orderId" to order.uid,
                        "status" to order.status.name,
                        "message" to (order.rejectReason ?: "Trading unavailable"),
                    )
                )
        } else {
            ResponseEntity.status(HttpStatus.ACCEPTED).body(order)
        }
    }

    @PostMapping("/fills")
    fun handleFill(@RequestBody request: OrderFillRequest): ResponseEntity<Void> {
        orderService.handleFill(request)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/{orderId}")
    fun getOrder(@PathVariable orderId: String): ResponseEntity<Order> {
        val order = orderService.getOrder(orderId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(order)
    }

    @GetMapping
    fun getOrders(@RequestParam portfolioId: String): ResponseEntity<List<Order>> {
        return ResponseEntity.ok(orderService.getOrdersByPortfolio(portfolioId))
    }
}
