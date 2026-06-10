package com.autobookkeeper.security;

import jakarta.servlet.http.HttpServletRequest;

public record AuthenticatedUser(String ownerKey) {

    private static final AuthenticatedUser DEFAULT = new AuthenticatedUser("default");

    public static AuthenticatedUser fromRequest(HttpServletRequest request) {
        Object value = request.getAttribute(ApiTokenFilter.AUTHENTICATED_USER_ATTRIBUTE);
        return value instanceof AuthenticatedUser user ? user : DEFAULT;
    }
}
