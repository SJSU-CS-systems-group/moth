package edu.sjsu.moth.server.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.nio.file.attribute.UserPrincipal;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Authentication object representing HTTP signed requests
 */
class HttpSignatureAuthentication implements Authentication {
    Map<String, String> headerFields;
    UserPrincipal principal;
    boolean authenticated;

    HttpSignatureAuthentication(Map<String, String> headerFields) {
        this.headerFields = headerFields;
        this.principal = () -> headerFields.get("keyid");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Set.of();
    }

    @Override
    public Object getCredentials() {
        return headerFields;
    }

    @Override
    public Object getDetails() {
        return headerFields;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return principal.getName();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + ": " + getName() + " " + headerFields;
    }
}
