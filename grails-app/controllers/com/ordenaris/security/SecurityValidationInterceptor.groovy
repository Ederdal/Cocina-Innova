package com.ordenaris.security
import com.ordenaris.Log

class SecurityValidationInterceptor {

    def springSecurityService

    SecurityValidationInterceptor() {
        match(controller:"*")
    }

    boolean before() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')

        def currentUser = springSecurityService.currentUser
        if (!currentUser) {
            return true
        }

        if (currentUser.accountLocked) {
            Log.logger(Log.WARN, logId, "Validación de seguridad", "Un usuario bloqueado intentó acceder", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}]")
            errorMessage(logId, 401, "Tu cuenta ha sido bloqueada.")
            return false
        }

        def jwtRoles = springSecurityService.authentication.authorities*.authority
        def realRoles = currentUser.authorities*.authority

        if (realRoles.toSet() != jwtRoles.toSet()) {
            Log.logger(Log.WARN, logId, "Validación de seguridad", "Los roles del usuario han cambiado, es necesario que vuelva a iniciar sesión", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}]")
            errorMessage(logId, 401, "Tus permisos han cambiado. Inicia sesión nuevamente por favor.")
            return false
        }

        return true
    }

    def errorMessage(logId, status, message){
        response.setContentType("application/json")
        response.setCharacterEncoding("UTF-8");
        response.setStatus(status)
        PrintWriter out = response.getWriter();
        HashMap resp = [ success:false, message: message, id:logId]
        out.println(resp.toPrettyString());
    }
}
