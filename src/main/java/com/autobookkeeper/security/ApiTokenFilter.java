package com.autobookkeeper.security;

import com.autobookkeeper.config.AutoBookkeeperProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    private final AutoBookkeeperProperties properties;

    public ApiTokenFilter(AutoBookkeeperProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!requiresToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String configuredToken = properties.apiToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedToken = request.getHeader("X-API-Token");
        if (!configuredToken.equals(providedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresToken(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ("POST".equalsIgnoreCase(request.getMethod()) && "/api/process".equals(path))
                || path.startsWith("/api/transactions");
    }
}
