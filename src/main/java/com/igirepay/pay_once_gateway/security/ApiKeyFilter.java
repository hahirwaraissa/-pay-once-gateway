package com.igirepay.pay_once_gateway.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class ApiKeyFilter implements Filter {

    @Value("${api.key:igirepay-secret-key}")
    private String apiKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip auth for H2 console
        if (httpRequest.getRequestURI().startsWith("/h2-console")) {
            chain.doFilter(request, response);
            return;
        }

        String requestKey = httpRequest.getHeader("X-API-KEY");

        if (apiKey.equals(requestKey)) {
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("Unauthorized: Invalid or missing API Key");
        }
    }
}
