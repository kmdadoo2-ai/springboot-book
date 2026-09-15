package com.study.spring.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String previous = MDC.get("requestId");
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        boolean failed = false;
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            int status = failed ? 500 : response.getStatus();
            log.info("http method={} status={} elapsedMs={}",
                    request.getMethod(), status, elapsed);
            if (previous == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previous);
            }
        }
    }
}
