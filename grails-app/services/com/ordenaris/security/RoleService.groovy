package com.ordenaris.security

import grails.gorm.transactions.Transactional
import com.ordenaris.Log
import com.ordenaris.TypeError
import com.ordenaris.security.UserRole

@Transactional
class RoleService {

    def listAllRoles(logId) {
        try {
            Log.logger( Log.INFO, logId, "Listar todos los roles.", "Servicio para listar todos los roles del sistema.")

            def roles = Role.list(sort: "authority", order: "asc").collect {
                mapRole(it)
            }

            Log.logger( Log.INFO, logId, "Listar todos los roles.", "Se consulto la informacion con exito.", null, "returnInformation:${roles.size()}")
            return [ data: [success: true, data: roles], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Listar todos los roles.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def getRoleInfo(uuid, logId) {
        try {
            Log.logger( Log.INFO, logId, "Obtener informacion de un rol.", "Servicio para obtener la información de un rol.", "uuid: ${uuid}")

            def role = Role.findByUuid(uuid)
            if (!role) {
                Log.logger( Log.WARN, logId, "Obtener información de un rol.", "Rol no encontrado.", "uuid: ${uuid}")
                return TypeError.informationNotFound(logId)
            }

            Log.logger( Log.INFO, logId, "Obtener información de un rol.", "Se consulto la información del rol exitosamente.", "uuid: ${uuid}", "role: [uuid: ${role.uuid}, authority: ${role.authority}]")
            return [ data: [success: true, data: mapRole(role)], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Obtener información de un rol.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def createNewRole(authority, logId) {
        try {
            Log.logger( Log.INFO, logId, "Crear un nuevo rol.", "Servicio para crear un nuevo rol.", "authority: ${authority}")

            if (Role.findByAuthority(authority)) {
                Log.logger( Log.WARN, logId, "Crear un nuevo rol.", "Ya existe un rol con la misma autoridad.", "authority: ${authority}")
                return TypeError.existingRegister(logId)
            }

            def role = new Role(authority)
            role.save(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Crear un nuevo rol.", "Rol creado exitosamente.", "authority: ${authority}", "role: [uuid: ${role.uuid}, authority: ${role.authority}]")
            return [ data  : [success: true, data: mapRole(role)], status: 201 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Crear un nuevo rol.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def changeAuthority(uuid, authority, logId) {
        try {
            Log.logger( Log.INFO, logId, "Cambiar la autoridad de un rol.", "Servicio para cambiar la autoridad de un rol.", "uuid: ${uuid}, authority: ${authority}")

            def role = Role.findByUuid(uuid)
            if (!role) {
                Log.logger( Log.WARN, logId, "Cambiar la autoridad de un rol.", "Rol no encontrado.", "uuid: ${uuid}, authority: ${authority}")
                return TypeError.informationNotFound(logId)
            }

            def sameAuthority = Role.findByAuthority(authority)
            if (sameAuthority) {
                Log.logger( Log.WARN, logId, "Cambiar la autoridad de un rol.", "Ya existe un rol con la misma autoridad.", "uuid: ${uuid}, authority: ${authority}")
                return TypeError.existingRegister(logId)
            }

            role.authority = authority
            role.save(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Cambiar la autoridad de un rol.", "Se cambio la autoridad correctamente.", "uuid: ${uuid}, authority: ${authority}", "role: [uuid: ${role.uuid}, authority: ${role.authority}]")
            return [ data  : [success: true, data: mapRole(role)], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Cambiar la autoridad de un rol.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def deleteRole(uuid, logId) {
        try {
            Log.logger( Log.INFO, logId, "Eliminar un rol.", "Servicio para eliminar un rol del sistema.", "uuid: ${uuid}")

            def role = Role.findByUuid(uuid)
            if (!role) {
                Log.logger( Log.WARN, logId, "Eliminar un rol.", "Rol no encontrado.", "uuid: ${uuid}")
                return TypeError.informationNotFound(logId)
            }

            if (UserRole.findByRole(role)) {
                Log.logger(Log.WARN, logId, "Eliminar un rol.", "No se puede eliminar el rol debido a que tiene usuarios asociados.", "uuid: ${uuid}")
                return TypeError.relationshipConflict(logId)
            }

            role.delete(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Eliminar un rol.", "Se elimino el rol con exito.", "uuid: ${uuid}")
            return [ data  : [success: true], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Eliminar un rol.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def mapRole(role) {
        return [
            uuid      : role.uuid,
            authority : role.authority
        ]
    }
}
