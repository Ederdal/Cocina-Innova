package com.ordenaris.security

import javax.servlet.http.HttpServletResponse
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.authentication.LockedException
import grails.plugin.springsecurity.rest.RestAuthenticationFailureHandler
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.authentication.InternalAuthenticationServiceException

class CustomRestAuthenticationFailureHandler extends RestAuthenticationFailureHandler {
    
    @Override
    void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        response.addHeader('WWW-Authenticate', 'X-Auth-Token')
        def errorMessage = exception?.message ?: "Error de autenticación"
        def errorType
         
        if (exception instanceof LockedException) {
            errorType = "Cuenta pendiente de activación"
            response.setStatus(423)
        } else if (exception instanceof BadCredentialsException) {
            errorType = "Error de autenticación"
            response.setStatus(401)
        } else if (exception instanceof InsufficientAuthenticationException) {
            errorType = "Información insuficiente"
            response.setStatus(403)
        } else if (exception instanceof InternalAuthenticationServiceException) {
            errorType = "Error interno"
            response.setStatus(500)
        } else {
            errorType = "No autorizado"
            response.setStatus(401)
        }

        response.setContentType("application/json")
        response.setCharacterEncoding("UTF-8")
        
        PrintWriter out = response.getWriter()
        HashMap resp = [success:false, message: errorMessage, error: errorType]
        out.println(resp.toPrettyString())
        out.flush()
    }
}
