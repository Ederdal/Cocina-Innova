package com.ordenaris.security

import grails.gorm.transactions.Transactional
import com.ordenaris.Constants
import com.ordenaris.Log
import com.ordenaris.TypeError

@Transactional
class UserRoleService {

    def getRolesByUser(uuid, logId) {
        try {
            Log.logger(Log.INFO, logId, "Obtener todos los roles de un usuario.", "Servicio para obtener todos los roles de un usuario.", "uuid: ${uuid}")

            def user = User.findByUuid(uuid)
            if (!user) {
                Log.logger( Log.WARN, logId, "Obtener todos los roles de un usuario.", "Usuario no encontrado.", "uuid: ${uuid}")
                return TypeError.informationNotFound(logId)
            }

            def roles = UserRole.findAllByUser(user).collect {
                [
                    uuid       : it.role.uuid,
                    authority: it.role.authority
                ]
            }

            Log.logger( Log.INFO, logId, "Obtener todos los roles de un usuario.", "Se consulto los roles del usuario exitosamente.", "uuid: ${uuid}", "returnInformation: ${roles.size()}")
            return [ data: [success: true, data: roles], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Obtener todos los roles de un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def assignRole(uuidUser, uuidRole, logId) {
        try {
            Log.logger(Log.INFO, logId, "Asignar un rol a un usuario.", "Servicio para asignar un rol a un usuario.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")

            def user = User.findByUuid(uuidUser, [lock: true])
            if (!user) {
                Log.logger(Log.WARN, logId, "Asignar un rol a un usuario.", "Usuario no encontrado.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")
                return TypeError.informationNotFound(logId)
            }

            def role = Role.findByUuid(uuidRole)
            if (!role) {
                Log.logger(Log.WARN, logId, "Asignar un rol a un usuario.", "Rol no encontrado.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")
                return TypeError.informationNotFound(logId)
            }

            def userRoles = user.getAuthorities()*.authority
            if (userRoles.contains(Constants.ROLE_CHEF) && role.authority in [Constants.ROLE_ADMIN, Constants.ROLE_FINANCE]) {
                Log.logger(Log.WARN, logId, "Asignar un rol a un usuario.", "El rol chef no puede coexistir con el rol admin o rol finance", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")
                return TypeError.externalPermissionMissing(logId, "la ausencia del rol chef")
            }

            if (role.authority == Constants.ROLE_CHEF && userRoles.any { it in [Constants.ROLE_ADMIN, Constants.ROLE_FINANCE] }) {
                Log.logger(Log.WARN, logId, "Asignar un rol a un usuario.", "No se puede asignar el rol chef a un usuario con rol admin o rol finance", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")
                return TypeError.externalPermissionMissing(logId, "la ausencia del rol admin y el rol finanzas")
            }

            if (UserRole.exists(user.id, role.id)) {
                Log.logger(Log.WARN, logId, "Asignar un rol a un usuario.", "El usuario ya cuenta con este rol", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")
                return TypeError.existingRegister(logId)
            }

            UserRole.create(user, role, true)

            Log.logger(Log.INFO, logId, "Asignar un rol a un usuario.", "Se asigno el rol correctamente al usuario", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}", "user: [username: ${user.username}, roles: ${user.getAuthorities()*.authority}]")
            return [ data: [success: true], status: 201 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Asignar un rol a un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def changeRole(uuidUser, uuidRole, uuidNewRole, logId) {
        try {
            Log.logger(Log.INFO, logId, "Cambiar un rol de un usuario.", "Servicio para cambiar un rol de un usuario.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}, uuidNewRole: ${uuidNewRole}")

            def user = User.findByUuid(uuidUser)
            if (!user) {
                Log.logger(Log.WARN, logId, "Cambiar un rol de un usuario.", "Usuario no encontrado.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}, uuidNewRole: ${uuidNewRole}")
                return TypeError.informationNotFound(logId)
            }

            def oldRole = Role.findByUuid(uuidRole)
            if (!oldRole) {
                Log.logger(Log.WARN, logId, "Cambiar un rol de un usuario.", "Rol no encontrado.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}, uuidNewRole: ${uuidNewRole}")
                return TypeError.informationNotFound(logId)
            }

            def newRole = Role.findByUuid(uuidNewRole)
            if (!newRole) {
                Log.logger(Log.WARN, logId, "Cambiar un rol de un usuario.", "Nuevo rol no encontrado.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}, uuidNewRole: ${uuidNewRole}")
                return TypeError.informationNotFound(logId)
            }

            if (!UserRole.exists(user.id, oldRole.id)) {
                Log.logger(Log.WARN, logId, "Cambiar un rol de un usuario.", "El usuario no cuenta con el rol a reemplazar.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}, uuidNewRole: ${uuidNewRole}")
                return TypeError.informationNotFound(logId)
            }

            if (UserRole.exists(user.id, newRole.id)) {
                Log.logger(Log.WARN, logId, "Cambiar un rol de un usuario.", "El usuario ya tiene el nuevo rol.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}, uuidNewRole: ${uuidNewRole}")
                return TypeError.existingRegister(logId)
            }

            UserRole.remove(user, oldRole)
            UserRole.create(user, newRole, true)

            Log.logger(Log.INFO, logId, "Cambiar un rol de un usuario.", "Se remplazaron los roles del usuario con exito.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}, uuidNewRole: ${uuidNewRole}", "user: [username: ${user.username}, roles: ${user.getAuthorities()*.authority}]")
            return [ data: [success: true], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Cambiar un rol de un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def removeRole(uuidUser, uuidRole, logId) {
        try {
            Log.logger(Log.INFO, logId, "Remover un rol a un usuario.", "Servicio para remover un rol a un usuario.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")

            def user = User.findByUuid(uuidUser)
            if (!user) {
                Log.logger(Log.WARN, logId, "Remover un rol a un usuario.", "Usuario no encontrado.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")
                return TypeError.informationNotFound(logId)
            }

            if (user.username == Constants.ADMIN) {
                Log.logger( Log.WARN, logId, "Remover un rol a un usuario.", "No es posible remover roles a esta cuenta.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")     
                return TypeError.resourceNotAvailable("que este usuario es el super administrador", logId)
            }

            def role = Role.findByUuid(uuidRole)
            if (!role) {
                Log.logger(Log.WARN, logId, "Remover un rol a un usuario.", "Rol no encontrado.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")
                return TypeError.informationNotFound(logId)
            }

            if (!UserRole.exists(user.id, role.id)) {
                Log.logger(Log.WARN, logId, "Remover un rol a un usuario.", "El usuario no cuenta con el rol a remover.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")
                return TypeError.informationNotFound(logId)
            }

            if (role.authority == Constants.ROLE_ADMIN && UserRole.countByRole(role) <= 1) {
                Log.logger(Log.WARN, logId, "Remover un rol a un usuario.", "Se intentó remover el rol de administrador al único usuario con este rol en el sistema.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}")
                return TypeError.resourceNotAvailable("que este usuario es el único con el rol de administrador en el sistema", logId)
            }

            UserRole.remove(user, role)

            Log.logger(Log.INFO, logId, "Remover un rol a un usuario.", "Se removio el rol exitosamente.", "uuidUser: ${uuidUser}, uuidRole: ${uuidRole}", "user: [username: ${user.username}, roles: ${user.getAuthorities()*.authority}]")
            return [ data: [success: true], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Remover un rol a un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }
}
