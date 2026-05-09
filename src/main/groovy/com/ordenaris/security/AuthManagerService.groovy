package com.ordenaris.security

import grails.transaction.Transactional
import org.springframework.dao.DataAccessException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.InsufficientAuthenticationException
import com.ordenaris.Log
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.authentication.InternalAuthenticationServiceException
import com.ordenaris.enums.RegisterTypeUser

@Service
class AuthManagerService{

    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException, DataAccessException {
        return userDetailsService.loadUserByUsername(username)
    }

    @Transactional(readOnly = true)
    AuthManagerBean loadUserByUsername(Authentication authentication, String logId) throws UsernameNotFoundException {
        try {
            Log.logger( Log.INFO, logId, "Login por Credenciales.", "Servicio para validar a un usuario del sistema.", "username: ${authentication.name}")

            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder()

            String username = authentication.name
            String crd = authentication.credentials.toString()

            User user = findUserByUsernameOrEmail(username)
            if (!user) {
                Log.logger( Log.WARN, logId, "Login por Credenciales.", "Usuario no registrado en el sistema.", "username: ${authentication.name}")
                throw new BadCredentialsException("Credenciales inválidas.")
            }

            if (user.registerType != RegisterTypeUser.CREDENTIALS) {
                Log.logger( Log.WARN, logId, "Login por Credenciales.", "La cuenta fue registrada con google, por lo cual no es posible continuar con la autenticación por este medio.", "username: ${authentication.name}")
                throw new InsufficientAuthenticationException("La cuenta fue registrada con google, porfavor de continuar por ese medio.")
            }

            if (!passwordEncoder.matches(crd, user.crd)) {
                Log.logger( Log.WARN, logId, "Login por Credenciales.", "El usuario ingreso mal sus credenciales.", "username: ${authentication.name}")
                throw new BadCredentialsException("Credenciales inválidas.")
            }

            Set<Role> roles = user.authorities as Set<Role>

            def authorities = roles.collect {
                new SimpleGrantedAuthority(it.authority)
            }

            if (user.accountLocked) {
                Log.logger( Log.WARN, logId, "Login por Credenciales.", "La cuenta se encuentra bloqueada.", "username: ${authentication.name}")
                throw new LockedException("Tu cuenta se encuentra bloqueada, por favor dirígete con un administrador para desbloquearla.")
            }

            if (!authorities) {
                Log.logger( Log.WARN, logId, "Login por Credenciales.", "La cuenta no cuenta con roles asignados por un administrador.", "username: ${authentication.name}")
                throw new InsufficientAuthenticationException("Tu cuenta no tiene roles asignados, comunicate con un administrador.")
            }

            Log.logger( Log.INFO, logId, "Login por Credenciales.", "Login exitoso.", "username: ${authentication.name}", "user: [uuid: ${user.uuid}, username: ${user.username}]")
            return new AuthManagerBean(
                user.username,     
                user.crd,
                user.enabled,
                !user.accountExpired,
                !user.passwordExpired,
                !user.accountLocked,
                authorities,
                user.id
            )

        } catch(BadCredentialsException | LockedException | InsufficientAuthenticationException e) {
            throw e
        } catch(e) {
            Log.logger( Log.ERROR, logId, "Login por Credenciales.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            throw new InternalAuthenticationServiceException("Se ha producido un error interno. Inténtelo de nuevo más tarde.")
        }
    }

    protected User findUserByUsernameOrEmail(String identifier) {

        if (!identifier) return null

        if (identifier.contains('@')) {
            return User.findByEmail(identifier)
        }

        return User.findByUsername(identifier)
    }
}