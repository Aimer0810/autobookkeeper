package com.autobookkeeper.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_USER_ATTRIBUTE = "authenticatedUser";
    private static final String PROCESS_PATH = "/api/process";
    private static final String TRANSACTIONS_PATH_PREFIX = "/api/transactions";

    private final UserTokenResolver userTokenResolver;

    public ApiTokenFilter(UserTokenResolver userTokenResolver) {
        this.userTokenResolver = userTokenResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!requiresToken(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!userTokenResolver.hasConfiguredTokens()) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedToken = request.getHeader("X-API-Token");
        AuthenticatedUser authenticatedUser = userTokenResolver.resolve(providedToken).orElse(null);
        if (authenticatedUser == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        request.setAttribute(AUTHENTICATED_USER_ATTRIBUTE, authenticatedUser);
        filterChain.doFilter(request, response);
    }

    private boolean requiresToken(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ("POST".equalsIgnoreCase(request.getMethod()) && PROCESS_PATH.equals(path))
                || path.startsWith(TRANSACTIONS_PATH_PREFIX);
    }
}
