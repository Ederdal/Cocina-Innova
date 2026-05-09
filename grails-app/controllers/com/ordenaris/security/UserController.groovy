package com.ordenaris.security

import grails.plugin.springsecurity.annotation.Secured
import java.nio.file.Files
import org.springframework.web.multipart.MultipartHttpServletRequest
import com.ordenaris.Constants
import com.ordenaris.Log
import com.ordenaris.TypeError

class UserController {
	static responseFormats = ['json', 'xml']
	
    def userService
    def springSecurityService

    @Secured(['permitAll'])
    def register() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Registrar nuevo usuario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${Log.sanitize(request.JSON)}")

        if (!request.JSON.username) {
            return respond(TypeError.missingParameter("nombre de usuario", logId, response))
        }
        if (!request.JSON.crd) {
            return respond(TypeError.missingParameter("crd", logId, response))
        }
        if (!request.JSON.email) {
            return respond(TypeError.missingParameter("correo", logId, response))
        }
        if (!request.JSON.names) {
            return respond(TypeError.missingParameter("nombres", logId, response))
        }
        if (!request.JSON.lastNames) {
            return respond(TypeError.missingParameter("apellido", logId, response))
        }

        if (!(request.JSON.username instanceof String)) {
            return respond(TypeError.incorrectFormat("nombre de usuario", "una cadena de texto", logId, response))
        }

        if (!(request.JSON.crd instanceof String)) {
            return respond(TypeError.incorrectFormat("crd", "una cadena de texto", logId, response))
        }

        if (!(request.JSON.email instanceof String)) {
            return respond(TypeError.incorrectFormat("correo", "una cadena de texto", logId, response))
        }

        if (!(request.JSON.names instanceof String)) {
            return respond(TypeError.incorrectFormat("nombres", "una cadena de texto", logId, response))
        }

        if (!(request.JSON.lastNames instanceof String)) {
            return respond(TypeError.incorrectFormat("apellido", "una cadena de texto", logId, response))
        }

        if (request.JSON.username.trim() != request.JSON.username) {
            return respond(TypeError.incorrectFormat("nombre de usuario", "sin espacios al principio ni al final", logId, response))
        }

        if (request.JSON.names.trim() != request.JSON.names) {
            return respond(TypeError.incorrectFormat("nombres", "sin espacios al principio ni al final", logId, response))
        }

        if (request.JSON.lastNames.trim() != request.JSON.lastNames) {
            return respond(TypeError.incorrectFormat("apellidos", "sin espacios al principio ni al final", logId, response))
        }

        if (!(request.JSON.crd.securePassword())) {
            return respond(TypeError.incorrectFormat("crd", "un crd sin espacios, tener entre 8 y 15 caracteres, incluir mayúsculas, minúsculas, un número y un carácter especial de esta lista [@!%*?&/]", logId, response))
        }

        def responseService = userService.registerUser(request.JSON, logId)

        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN', 'ROLE_FINANCE'])
    def paginateUsers() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Paginar usuarios.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        if (!params.page) {
            return respond(TypeError.missingParameter("pagina", logId, response))
        }

        if (!params.max) {
            return respond(TypeError.missingParameter("maximo", logId, response))
        }

        if (!params.page.onlyNumbers()) {
            return respond(TypeError.incorrectFormat("pagina", "numeros", logId, response))
        }

        if (!params.max.onlyNumbers()) {
            return respond(TypeError.incorrectFormat("maximo", "numeros", logId, response))
        }

        if (!(params.max.toInteger() in [5, 10, 20, 50, 100])) {
            return respond(TypeError.incorrectFormat("maximo", "[5,10,20,50,100]", logId, response))
        }

        if (params.orderColumn && !(params.orderColumn in ["username", "email"])) {
            return respond(TypeError.incorrectFormat("ordenar por columna", "[username, email]", logId, response))
        }

        if (params.order && !(params.order in ["asc", "desc"])) {
            return respond(TypeError.incorrectFormat("orden", "[asc, desc]", logId, response))
        }

        def responseService = userService.paginateUsers(params, logId)

        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN', 'ROLE_FINANCE'])
    def changeStatus() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Cambiar status.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def user = springSecurityService.currentUser

        def responseService = userService.changeStatus(params, user, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['isAuthenticated()'])
    def getUserInfo() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Obtener información de un usuario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def user = springSecurityService.currentUser

        def responseService = userService.getUserInfo(user, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN'])
    def changeUserCrd() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Cambiar crd de un usuario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        if (!request.JSON.newCrd) {
            return respond(TypeError.missingParameter("nueva crd", logId, response))
        }

        if (!(request.JSON.newCrd instanceof String)) {
            return respond(TypeError.incorrectFormat("nueva crd", "una cadena de texto", logId, response))
        }

        if (!(request.JSON.newCrd.securePassword())) {
            return respond(TypeError.incorrectFormat("nueva crd", "un crd sin espacios, tener entre 8 y 15 caracteres, incluir mayúsculas, minúsculas, un número y un carácter especial de esta lista [@!%*?&/]", logId, response))
        }

        def responseService = userService.adminChangeCrd(params.uuid, request.JSON.newCrd, logId)

        return respond(responseService.data, status: responseService.status)
    }
    
    @Secured(['permitAll'])
    def updateChangeCrdRequest(){
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Actualizar la solicitud de cambio de crd.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def statusIsRequest = "request".equals(params.status)
        def currentUser = springSecurityService.currentUser

        if (!currentUser && !statusIsRequest) {
            return respond(TypeError.noPermissions(logId, response))
        }

        if (!currentUser && statusIsRequest) {
            if (!request.JSON.email) {
                return respond(TypeError.missingParameter("correo", logId, response))
            }

            if (!(request.JSON.email instanceof String)) {
                return respond(TypeError.incorrectFormat("correo", "una cadena de texto", logId, response))
            }
        }

        def responseService = userService.updateChangeCrdRequest(currentUser, statusIsRequest, request.JSON.email, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['isAuthenticated()'])
    def changeProfilePicture() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Cambiar foto de perfil.", "Iniciando la solicitud.", "params: ${params}")
        
        if (!(request instanceof org.springframework.web.multipart.MultipartHttpServletRequest)) { 
            return respond(TypeError.incorrectFormat("request", "un form-data", logId, response))
        }

        if (request.postSizeExceeded) {
            return respond(TypeError.contentTooLarge(logId, response))
        }

        if (!request.getFileMap().file) {
            return respond(TypeError.missingParameter("archivo", logId, response))
        }

        def file = request.getFile("file")

        if (file.empty) {
            return respond(TypeError.incorrectFormat("archivo", "algun tipo de archivo digital", logId, response))
        }

        if (!Constants.ALLOWED_TYPES.contains(file.contentType)) {
            return respond(TypeError.incorrectFormat("archivo", "[image/jpeg, image/png, image/webp]", logId, response))
        }

        if (file.size > Constants.MAX_SIZE) {
            return respond(TypeError.incorrectFormat("archivo", "una imagen no mayor a 2MB", logId, response))
        }
        
        def user = springSecurityService.currentUser

        def responseService = userService.changeProfilePicture(user, file, logId)
        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['isAuthenticated()'])
    def getProfilePicture() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Obtener foto de perfil.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def user = springSecurityService.currentUser

        def responseService = userService.getProfilePicture(user, logId)
        if( responseService.status != 200 ) {
            return respond(responseService.data, status: responseService.status)
        }

        response.contentType = Files.probeContentType(responseService.data.data.image.toPath())

        response.outputStream << responseService.data.data.image.bytes
        response.outputStream.flush()
    }

    @Secured(['ROLE_ADMIN'])
    def changeUserProfilePicture() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Cambiar foto de perfil de un usuario.", "Iniciando la solicitud.", "params: ${params}")

        if (!(request instanceof org.springframework.web.multipart.MultipartHttpServletRequest)) { 
            return respond(TypeError.incorrectFormat("request", "un form-data", logId, response))
        }

        if (request.postSizeExceeded) {
            return respond(TypeError.contentTooLarge(logId, response))
        }

        if (!request.getFileMap().file) {
            return respond(TypeError.missingParameter("archivo", logId, response))
        }

        def file = request.getFile("file")

        if (file.empty) {
            return respond(TypeError.incorrectFormat("archivo", "algun tipo de archivo digital", logId, response))
        }

        if (!Constants.ALLOWED_TYPES.contains(file.contentType)) {
            return respond(TypeError.incorrectFormat("archivo", "[image/jpeg, image/png, image/webp]", logId, response))
        }

        if (file.size > Constants.MAX_SIZE) {
            return respond(TypeError.incorrectFormat("archivo", "una imagen no mayor a 2MB", logId, response))
        }

        def responseService = userService.changeUserProfilePicture(params.uuid, file, logId)

        return respond(responseService.data, status: responseService.status)
    }

    @Secured(['ROLE_ADMIN', 'ROLE_FINANCE'])
    def getUserProfilePicture() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Obtener foto de perfil de un usuario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = userService.getUserProfilePicture(params.uuid, logId)
        if( responseService.status != 200 ) {
            return respond(responseService.data, status: responseService.status)
        }

        response.contentType = Files.probeContentType(responseService.data.data.image.toPath())

        response.outputStream << responseService.data.data.image.bytes
        response.outputStream.flush()
    }
}
