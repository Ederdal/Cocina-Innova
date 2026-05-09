package com.ordenaris.security

import grails.plugin.springsecurity.annotation.Secured
import com.ordenaris.Log
import com.ordenaris.TypeError

class RoleController {

    static responseFormats = ['json', 'xml']

    def roleService

    @Secured(['ROLE_ADMIN'])
    def listAllRoles() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Listar todos los roles.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = roleService.listAllRoles(logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN'])
    def getRoleInfo() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Obtener informacion de un rol.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = roleService.getRoleInfo(params.uuid, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN'])
    def createNewRole() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Crear un nuevo rol.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        if (!request.JSON.authority) {
            return respond(TypeError.missingParameter("autoridad", logId, response))
        }

        if (!(request.JSON.authority instanceof String)) {
            return respond(TypeError.incorrectFormat("autoridad", "una cadena de texto", logId, response))
        }

        if (!(request.JSON.authority.roleFormat())) {
            return respond(TypeError.incorrectFormat("autoridad", "una autoridad que empiece con la palabra exacta 'ROLE_', solo tener mayúsculas y no contener espacios usar '_' en su lugar", logId, response))
        }

        def responseService = roleService.createNewRole(request.JSON.authority, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN'])
    def changeAuthority() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Cambiar la autoridad de un rol.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        if (!request.JSON.authority) {
            return respond(TypeError.missingParameter("autoridad", logId, response))
        }

        if (!(request.JSON.authority instanceof String)) {
            return respond(TypeError.incorrectFormat("autoridad", "una cadena de texto", logId, response))
        }

        if (!(request.JSON.authority.roleFormat())) {
            return respond(TypeError.incorrectFormat("autoridad", "una autoridad que empiece con la palabra exacta 'ROLE_', solo tener mayúsculas y no contener espacios usar '_' en su lugar", logId, response))
        }

        def responseService = roleService.changeAuthority(params.uuid, request.JSON.authority, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN'])
    def deleteRole() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Eliminar un rol.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = roleService.deleteRole(params.uuid, logId)
        return respond(responseService.data, status: responseService.status)
    }
}
