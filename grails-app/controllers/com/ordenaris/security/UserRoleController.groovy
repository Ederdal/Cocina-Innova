package com.ordenaris.security

import grails.plugin.springsecurity.annotation.Secured
import com.ordenaris.Log
import com.ordenaris.TypeError

class UserRoleController {

    static responseFormats = ['json', 'xml']

    def userRoleService

    @Secured(['ROLE_ADMIN'])
    def getRolesByUser() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Obtener todos los roles de un usuario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = userRoleService.getRolesByUser(params.uuidUser, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN'])
    def assignRole() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Asignar un rol a un usuario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = userRoleService.assignRole(params.uuidUser, params.uuidRole, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN'])
    def changeRole() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Cambiar un rol de un usuario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = userRoleService.changeRole(params.uuidUser, params.uuidRole, params.uuidNewRole, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN'])
    def removeRole() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Remover un rol a un usuario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")
        
        def responseService = userRoleService.removeRole(params.uuidUser, params.uuidRole, logId)
        return respond(responseService.data, status: responseService.status)
    }
}
