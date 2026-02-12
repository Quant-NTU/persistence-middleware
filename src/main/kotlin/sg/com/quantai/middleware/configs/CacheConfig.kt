package sg.com.quantai.middleware.configs

import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Cache configuration for Analytics API
 * 
 * Uses Caffeine cache (in-memory, high-performance) as default implementation.
 * Provides TTL-based expiration and size-based eviction to prevent memory issues.
 * 
 * For distributed systems, this can be replaced with Redis by:
 * 1. Adding spring-boot-starter-data-redis dependency
 * 2. Configuring RedisCacheManager instead of CaffeineCacheManager
 * 3. Setting spring.cache.type=redis in application.properties
 * 
 * @see AnalyticsService for cache usage (@Cacheable annotations)
 */
@Configuration
@EnableCaching
class CacheConfig {

    private val logger = LoggerFactory.getLogger(CacheConfig::class.java)

    /**
     * Time-to-live for cache entries (in seconds)
     * After this period, entries are automatically evicted
     * Default: 300 seconds (5 minutes)
     */
    @Value("\${app.cache.ttl-seconds:300}")
    private val ttlSeconds: Long = 300

    /**
     * Maximum number of entries per cache
     * Prevents unbounded memory growth
     * Uses LRU (Least Recently Used) eviction policy
     * Default: 10,000 entries
     */
    @Value("\${app.cache.max-size:10000}")
    private val maxSize: Long = 10000

    /**
     * Enable cache statistics recording
     * Useful for monitoring hit rates and performance
     * Default: true
     */
    @Value("\${app.cache.record-stats:true}")
    private val recordStats: Boolean = true

    @Bean
    fun cacheManager(): CacheManager {
        logger.info("Configuring Caffeine cache with TTL=${ttlSeconds}s, maxSize=$maxSize, recordStats=$recordStats")
        
        val caffeineCacheManager = CaffeineCacheManager()
        
        // Configure Caffeine cache builder
        caffeineCacheManager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)  // TTL-based expiration
                .maximumSize(maxSize)                             // Size-based eviction (LRU)
                .recordStats()                                    // Enable statistics
                .also { builder ->
                    if (recordStats) {
                        // Log cache statistics periodically (optional, can be removed in production)
                        logger.debug("Cache statistics recording enabled")
                    }
                }
        )
        
        // Define cache names explicitly
        caffeineCacheManager.setCacheNames(listOf("analytics"))
        
        logger.info("Caffeine cache manager initialized successfully")
        return caffeineCacheManager
    }
}

/**
 * Cache Configuration Guide
 * =========================
 * 
 * ## Current Implementation (In-Memory)
 * 
 * - **Cache Provider**: Caffeine (high-performance, Google Guava successor)
 * - **Storage**: JVM heap memory
 * - **Eviction Policy**: LRU (Least Recently Used)
 * - **Expiration**: Time-based (TTL after write)
 * - **Scope**: Single application instance
 * 
 * ## Configuration Properties
 * 
 * Add to application.properties:
 * ```properties
 * # Cache TTL in seconds (default: 300 = 5 minutes)
 * app.cache.ttl-seconds=300
 * 
 * # Maximum entries per cache (default: 10,000)
 * app.cache.max-size=10000
 * 
 * # Enable cache statistics (default: true)
 * app.cache.record-stats=true
 * ```
 * 
 * ## Memory Considerations
 * 
 * ### Estimated Memory Usage per Entry:
 * - Cache Key: ~200 bytes (string concatenation of parameters)
 * - Cache Value: Varies by endpoint
 *   - VolumeStats: ~500 bytes per object
 *   - PriceTrends: ~600 bytes per object
 *   - VolatilityMetrics: ~550 bytes per object
 *   - ComparisonResult: ~1KB per comparison
 * 
 * ### Total Memory Calculation:
 * - Average entry size: ~1KB (key + value + overhead)
 * - Max entries: 10,000 (configurable)
 * - **Total cache memory: ~10MB** (safe for most deployments)
 * 
 * ### JVM Heap Recommendations:
 * - Minimum heap: 512MB (-Xmx512m)
 * - Recommended heap: 1GB+ for production
 * - Reserve 10-15% of heap for cache
 * 
 * ## Cache Eviction Triggers
 * 
 * 1. **Time-based**: Entry expires after TTL (default: 5 minutes)
 * 2. **Size-based**: Oldest accessed entry evicted when max size reached
 * 3. **Manual**: Can be cleared via CacheManager API
 * 
 * ## Monitoring Cache Health
 * 
 * ### Via Spring Boot Actuator:
 * ```bash
 * GET /actuator/metrics/cache.size?tag=cache:analytics
 * GET /actuator/metrics/cache.gets?tag=cache:analytics,result:hit
 * GET /actuator/metrics/cache.gets?tag=cache:analytics,result:miss
 * ```
 * 
 * ### Programmatic Access:
 * ```kotlin
 * @Autowired
 * lateinit var cacheManager: CacheManager
 * 
 * fun getCacheStats(): Map<String, Any> {
 *     val cache = cacheManager.getCache("analytics") as CaffeineCache
 *     val stats = cache.nativeCache.stats()
 *     return mapOf(
 *         "hitCount" to stats.hitCount(),
 *         "missCount" to stats.missCount(),
 *         "hitRate" to stats.hitRate(),
 *         "evictionCount" to stats.evictionCount(),
 *         "size" to cache.nativeCache.estimatedSize()
 *     )
 * }
 * ```
 * 
 * ## Migration to Redis (Distributed Cache)
 * 
 * ### Why Migrate?
 * - Multiple application instances (horizontal scaling)
 * - Cache persistence across restarts
 * - Larger cache capacity (not limited by JVM heap)
 * - Centralized cache management
 * 
 * ### Migration Steps:
 * 
 * 1. **Add Redis dependency** (build.gradle):
 * ```gradle
 * implementation("org.springframework.boot:spring-boot-starter-data-redis")
 * implementation("redis.clients:jedis:4.3.1")  // or lettuce
 * ```
 * 
 * 2. **Configure Redis** (application.properties):
 * ```properties
 * spring.cache.type=redis
 * spring.redis.host=localhost
 * spring.redis.port=6379
 * spring.redis.password=${REDIS_PASSWORD}
 * spring.cache.redis.time-to-live=300000  # 5 minutes in milliseconds
 * ```
 * 
 * 3. **Update CacheConfig**:
 * ```kotlin
 * @Bean
 * fun cacheManager(redisConnectionFactory: RedisConnectionFactory): CacheManager {
 *     val config = RedisCacheConfiguration.defaultCacheConfig()
 *         .entryTtl(Duration.ofSeconds(ttlSeconds))
 *         .serializeValuesWith(
 *             RedisSerializationContext.SerializationPair.fromSerializer(
 *                 GenericJackson2JsonRedisSerializer()
 *             )
 *         )
 *     
 *     return RedisCacheManager.builder(redisConnectionFactory)
 *         .cacheDefaults(config)
 *         .build()
 * }
 * ```
 * 
 * 4. **No code changes needed** in AnalyticsService - @Cacheable annotations work the same!
 * 
 * ### Redis vs Caffeine Trade-offs:
 * 
 * | Feature | Caffeine (Current) | Redis (Future) |
 * |---------|-------------------|----------------|
 * | Latency | ~0.1ms (in-memory) | ~1ms (network) |
 * | Throughput | 10M+ ops/sec | 100K+ ops/sec |
 * | Capacity | Limited by JVM heap | Limited by Redis memory |
 * | Shared across instances | No | Yes |
 * | Persistence | No | Optional |
 * | Complexity | Low | Medium |
 * | Cost | Free (uses app memory) | Additional infrastructure |
 * 
 * ## Cache Warming Strategy
 * 
 * For frequently accessed data, pre-populate cache on startup:
 * 
 * ```kotlin
 * @EventListener(ApplicationReadyEvent::class)
 * fun warmUpCache() {
 *     logger.info("Warming up cache...")
 *     
 *     // Pre-fetch common queries
 *     val popularSymbols = listOf("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA")
 *     val last30Days = LocalDateTime.now().minusDays(30)
 *     val now = LocalDateTime.now()
 *     
 *     popularSymbols.forEach { symbol ->
 *         try {
 *             analyticsService.getPriceTrends(
 *                 symbols = listOf(symbol),
 *                 startDate = last30Days,
 *                 endDate = now
 *             )
 *         } catch (e: Exception) {
 *             logger.warn("Failed to warm cache for $symbol", e)
 *         }
 *     }
 *     
 *     logger.info("Cache warming completed")
 * }
 * ```
 * 
 * ## Cache Invalidation Strategies
 * 
 * ### 1. Time-based (Current):
 * - Automatic via TTL
 * - Simple but may serve stale data
 * 
 * ### 2. Event-based (Future Enhancement):
 * ```kotlin
 * @EventListener
 * fun onDataWarehouseRefresh(event: DataWarehouseRefreshEvent) {
 *     cacheManager.getCache("analytics")?.clear()
 *     logger.info("Analytics cache cleared after data warehouse refresh")
 * }
 * ```
 * 
 * ### 3. Manual via API:
 * ```kotlin
 * @PostMapping("/analytics/cache/clear")
 * fun clearCache(): ResponseEntity<String> {
 *     cacheManager.getCache("analytics")?.clear()
 *     return ResponseEntity.ok("Cache cleared successfully")
 * }
 * ```
 * 
 * ## Performance Tuning
 * 
 * ### If Hit Rate is Low (<70%):
 * - Increase TTL (data doesn't change often)
 * - Increase max size (more entries cached)
 * - Review query patterns (too many unique queries)
 * 
 * ### If Memory Usage is High:
 * - Decrease max size
 * - Decrease TTL
 * - Consider moving to Redis
 * 
 * ### If Response Time Increases:
 * - Check eviction count (frequent evictions = thrashing)
 * - Increase max size or add more memory
 * - Profile serialization overhead
 */

