package com.dataops.basecamp.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Trace ID Filter
 *
 * Generates or propagates a unique trace ID for each request.
 * The trace ID is stored in MDC for logging and audit purposes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : OncePerRequestFilter() {
    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // Use client-provided Trace ID or generate a new one
        val traceId =
            request.getHeader(TRACE_ID_HEADER)
                ?: UUID.randomUUID().toString()

        try {
            // Store in MDC for logging and AuditAspect
            MDC.put(TRACE_ID_KEY, traceId)

            // Include in response header for client tracking
            response.setHeader(TRACE_ID_HEADER, traceId)

            filterChain.doFilter(request, response)
        } finally {
            // Clean up after request completion (prevent memory leak)
            MDC.remove(TRACE_ID_KEY)
        }
    }
}
