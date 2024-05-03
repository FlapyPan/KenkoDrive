package com.akagiyui.drive.component.limiter

import com.akagiyui.common.delegate.LoggerDelegate
import com.akagiyui.common.exception.TooManyRequestsException
import io.github.bucket4j.Bucket
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 限流 AOP
 * @author AkagiYui
 */
@Aspect
@Component
class FrequencyLimitAspect {
    private val log by LoggerDelegate()

    /**
     * 限流器字典
     * map的key为 Limit.key
     */
    private val limitMap: MutableMap<String, Bucket> = ConcurrentHashMap()

    @Around("@annotation(Limit)")
    @Throws(Throwable::class)
    fun around(joinPoint: ProceedingJoinPoint): Any? {
        // 获取方法签名
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        // 获取 Limit 注解
        val limitAnnotation = method.getAnnotation(Limit::class.java)
        if (limitAnnotation != null) {
            val rateLimiter = limitMap.computeIfAbsent(limitAnnotation.key) {
                // 创建令牌桶
                log.debug("Created RateLimiter: {}", limitAnnotation.key)
                Bucket.builder().addLimit {
                    it.capacity(limitAnnotation.permitsPerSecond)
                        .refillGreedy(limitAnnotation.permitsPerSecond, Duration.ofSeconds(1))
                        .initialTokens(limitAnnotation.permitsPerSecond)
                }.build()
            }
            // 获取令牌
            val duration = Duration.ofMillis(limitAnnotation.timeunit.toMillis(limitAnnotation.timeout))
            val acquire = rateLimiter.asBlocking().tryConsume(1, duration)
            // 拿不到令牌，抛出异常
            if (!acquire) {
                log.debug("Too many requests: {}", limitAnnotation.key)
                throw TooManyRequestsException()
            }
        }
        return joinPoint.proceed()
    }
}
