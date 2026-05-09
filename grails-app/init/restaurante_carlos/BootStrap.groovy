package restaurante_carlos

import groovy.json.JsonBuilder;
import java.util.regex.*
import com.ordenaris.security.Role
import com.ordenaris.security.User
import com.ordenaris.security.UserRole 
class BootStrap {

    def SettingsService

    def init = { servletContext ->
        
        String.metaClass.DISH_DESCRIPTION_PATTERN = /^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\s.,!?\-()]+$/
        String.metaClass.DISH_COST_PATTERN = /^\d+(\.\d{1,2})?$/
        String.metaClass.onlyNumbers = {
            def expresion = '^[0-9]*$' 
            def patter = Pattern.compile(expresion)
            def match = patter.matcher(delegate)
            return match.matches()
        }

        String.metaClass.roleFormat = {
            def expression = '^ROLE_[A-Z_]+$'
            def patter = Pattern.compile(expression)
            def match = patter.matcher(delegate)
            return match.matches()
        }

        String.metaClass.isHourFormat = {
            def expression = /^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$/
            def patter = Pattern.compile(expression)
            def match = patter.matcher(delegate)
            return match.matches()
        }

        String.metaClass.securePassword = {
            def expresion = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[$@!%*?&\/])[A-Za-z\d$@!%*?&\/]{8,15}$/
            def pattern = Pattern.compile(expresion)
            def matcher = pattern.matcher(delegate)
            return matcher.matches()
        }

        String.metaClass.isUuid = {
            def uuidPattern = '^[a-fA-F0-9]{32}$'
            def pattern = Pattern.compile(uuidPattern)
            def matcher = pattern.matcher(delegate)
            return matcher.matches()
        }
        
        String.metaClass.isMultipleUuid = {
            def uuidValues = '^[a-fA-F0-9,]*$'
            def pattern = Pattern.compile(uuidValues)
            def matcher = pattern.matcher(delegate)
            return matcher.matches()
        }

        Object.metaClass.toPrettyString = {
            try {
                return new JsonBuilder(delegate).toPrettyString().replaceAll('\n', '').replaceAll('    ', '')
            }catch(e) {
                return '{ERROR-AL-GENERAL-JSON}'
            }
        }

        // Crear roles básicos
        if( Role.count() == 0 ) {
            Role.findOrSaveByAuthority('ROLE_ADMIN')
            Role.findOrSaveByAuthority('ROLE_CHEF')
            Role.findOrSaveByAuthority('ROLE_FINANCE')
            Role.findOrSaveByAuthority('ROLE_EMPLOYEE')
            Role.findOrSaveByAuthority('ROLE_USER')
            Role.findOrSaveByAuthority('ROLE_NO_ROLES')
        }

        // Crear usuario de prueba
        if( User.count() == 0 ) {
            def adminRole = Role.findByAuthority('ROLE_ADMIN')
            
            def adminUser = new User(
                username: 'admin',
                crd: 'Contrasen@1',
                email: 'admin@ordenaris.com',
                names: 'Admin',
                lastNames: 'Test',
                enabled: true,
                accountLocked: false,
                accountExpired: false,
                passwordExpired: false
            )
            adminUser.save(flush: true, failOnError: true)
            
            UserRole.create adminUser, adminRole
            UserRole.withSession { session ->
                session.flush()
                session.clear()
            }
        }

        SettingsService.registerInitData()
        SettingsService.updateInfoDB("Actualizando settings")

    }
    def destroy = {
    }
}
