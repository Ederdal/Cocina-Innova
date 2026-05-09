package com.ordenaris.security

import groovy.transform.CompileStatic
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import grails.plugin.springsecurity.rest.oauth.OauthUser
import grails.plugin.springsecurity.rest.oauth.OauthUserDetailsService
import org.pac4j.core.profile.CommonProfile
import org.pac4j.oauth.profile.OAuth20Profile
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.core.AuthenticationException
import java.security.SecureRandom
import com.ordenaris.enums.RegisterTypeUser
import com.ordenaris.security.User
import com.ordenaris.Log
import com.ordenaris.Conf
import com.ordenaris.Constants
import java.util.List

@Slf4j
@CompileStatic
class DefaultOauthUserDetailsService implements OauthUserDetailsService {

    private static final String CRD_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789$/@!%*?&()-_=+[]{}<>'

    private static final SecureRandom secureRandom = new SecureRandom()

    @Delegate
    AuthManagerService authManagerService

    @Override
    OauthUser loadUserByUserProfile(CommonProfile profile, Collection<GrantedAuthority> defaultRoles) throws UsernameNotFoundException {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Login por Google.", "Iniciando la solicitud.", "email: ${profile.email} firstName: ${profile.firstName}, familyName: ${profile.familyName}")

        if (!(profile instanceof OAuth20Profile)) {
            throw new UsernameNotFoundException("Perfil OAuth no compatible")
        }

        OAuth20Profile oauthProfile = (OAuth20Profile) profile

        if (!oauthProfile.email) {
            throw new UsernameNotFoundException("Google no regreso un email")
        }

        return validateUser(oauthProfile, logId)
    }

    protected OauthUser validateUser(OAuth20Profile profile, String logId) {
        try {
            Log.logger( Log.INFO, logId, "Login por Google.", "Servicio para validar a un usuario de google.", "email: ${profile.email} firstName: ${profile.firstName}, familyName: ${profile.familyName}")
            
            String configValue = Conf.findConfiguration(Constants.VALID_EMAIL_DOMAINS)
            if (!configValue) {
                Log.logger(Log.ERROR, logId, "Login por Google.", "Configuración VALID_EMAIL_DOMAINS no encontrada.", "email: ${profile.email} firstName: ${profile.firstName}, familyName: ${profile.familyName}")
                throw new InternalAuthenticationServiceException("Se ha producido un error interno. Inténtelo de nuevo más tarde.")
            }

            List<String> validEmailDomains = configValue.split(",").toList()*.trim()
            if (!validEmailDomains.any { String domain -> profile.email.endsWith(domain) }) {
                Log.logger( Log.WARN, logId, "Login por Google.", "El dominio del email no es valido.", "email: ${profile.email} firstName: ${profile.firstName}, familyName: ${profile.familyName}")
                throw new UsernameNotFoundException("Solo se permiten los siguientes dominios de correo ${validEmailDomains}")
            }

            User domainUser = findUserByEmail(profile.email)
            if (domainUser) {

                if (domainUser.registerType != RegisterTypeUser.GOOGLE) {
                    Log.logger( Log.WARN, logId, "Login por Google.", "La cuenta fue registrada por credenciales, por lo cual no es posible continuar con la autenticación por este medio.", "email: ${profile.email} firstName: ${profile.firstName}, familyName: ${profile.familyName}")
                    throw new InsufficientAuthenticationException("La cuenta fue registrada por credenciales, favor de continuar la auntenticacion por ese medio")
                }

                if (domainUser.accountLocked) {
                    Log.logger( Log.WARN, logId, "Login por Google.", "La cuenta se encuentra bloqueada", "email: ${profile.email} firstName: ${profile.firstName}, familyName: ${profile.familyName}")
                    throw new LockedException("Tu cuenta debe ser desbloqueada por un administrador")
                }

                Collection<GrantedAuthority> roles = domainUser.authorities
                    .findAll { it.authority != 'ROLE_NO_ROLES' }
                    .collect { (GrantedAuthority) new SimpleGrantedAuthority(it.authority) }

                if (!roles) {
                    Log.logger( Log.WARN, logId, "Login por Google.", "La cuenta no cuenta con roles asignados por un administrador.", "email: ${profile.email} firstName: ${profile.firstName}, familyName: ${profile.familyName}")
                    throw new InsufficientAuthenticationException("Tu cuenta no tiene roles asignados por un administrador")
                }

                Log.logger( Log.INFO, logId, "Login por Google.", "Logeo exitoso", "email: ${profile.email} firstName: ${profile.firstName}, familyName: ${profile.familyName}", "user: [uuid: ${domainUser.uuid}, username: ${domainUser.username}]")
                return new OauthManagerBean(
                    domainUser.username,
                    domainUser.crd,
                    roles,
                    profile,
                    domainUser.id
                )  
            }

            String usernameExtracted = extractUsername(profile.email)
            while (findUserByUsername(usernameExtracted)) {
                Integer num = new Random().nextInt(9000) + 1000
                usernameExtracted = extractUsername(profile.email) + "${num}"
            }

            User user = new User(
                username: usernameExtracted,
                crd: generateSecureCrd(),
                email: profile.email,
                names: profile.firstName ?: "",
                lastNames: profile.familyName ?: "",
                registerType: RegisterTypeUser.GOOGLE,
                enabled: true,
                accountLocked: true,
                accountExpired: false,
                passwordExpired: false
            )

            user.save(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Login por Google.", "Se registro un nuevo usuario de google en espera de autorización por admin", "email: ${profile.email} firstName: ${profile.firstName}, familyName: ${profile.familyName}", "user: [uuid: ${user.uuid}, username: ${user.username}]")
            throw new LockedException("Usuario pendiente de autorizacion por administrador")
            
        } catch(AuthenticationException e) {
            throw e
        } catch(e) {
            Log.logger( Log.ERROR, logId, "Login por Google.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            throw new InternalAuthenticationServiceException("Se ha producido un error interno. Inténtelo de nuevo más tarde.")
        }
    }

    protected String extractUsername(String email) {
        return email.substring(0, email.indexOf('@'))
    }

    protected String generateSecureCrd(int length = 24) {
        StringBuilder crd = new StringBuilder(length)
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(CRD_CHARS.length())
            crd.append(CRD_CHARS.charAt(index))
        }
        crd.toString()
    }

    @CompileDynamic
    protected User findUserByEmail(String email) {
        User.findByEmail(email)
    }

    @CompileDynamic
    protected User findUserByUsername(String username) {
        User.findByUsername(username)
    }
}
