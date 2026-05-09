package com.ordenaris.security

import grails.compiler.GrailsCompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import com.ordenaris.Log

@Service
@GrailsCompileStatic
class CustomAuthenticationProvider implements AuthenticationProvider{

    @Autowired
    private AuthManagerService authManagerService

    @Override
    Authentication authenticate(Authentication authentication) throws AuthenticationException {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Login por Credenciales.", "Iniciando la solicitud.", "username: ${authentication.name}")

        UserDetails user = authManagerService.loadUserByUsername(authentication, logId)

        return new UsernamePasswordAuthenticationToken(
            user,
            null,
            user.authorities
        )
    }

    @Override
    boolean supports(Class<?> authentication) {
        return (UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication));
    }
}