package com.ordenaris.restaurant
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import com.ordenaris.TypeError
import com.ordenaris.Log


class MenuController {
    static responseFormats = ['json', 'xml']
    def menuService
    SpringSecurityService springSecurityService

    private static class MenuValidationException extends RuntimeException {
        def resp
        Integer status

        MenuValidationException(def resp, Integer status) {
            super("Menu validation error")
            this.resp = resp
            this.status = status
        }
    }

    private MenuType findMenuTypeOrRespond(String uuid, String logId) {
        def result = menuService.validateMenuTypeForInfo(uuid, logId)
        if (result instanceof Map && result.status) {
            throw new MenuValidationException(result.resp, result.status as Integer)
        }
        return result
    }

    private String extractAndValidateMenuTypeName(def data) {
        def name = data.name?.trim()
        Log.logger(Log.INFO, null, "Nuevo tipo de menú", "Validando nombre", "name: ${name}")
        validateMenuTypeName(name)
        return name
    }

    private boolean menuTypeNameExists(String name) {
        return menuService.menuTypeNameExists(name)
    }

    private respondNameExists(String name, String logId) {
        Log.logger(Log.WARN, logId, "Nuevo tipo de menú", "El nombre ya existe", "name: ${name}")
        respond([success: false, message: "El nombre ya existe"], status: 409)
    }
    
    private void validateMenuTypeName(def name) {
        if (!name || !name.toString().trim()) {
            throw new MenuValidationException(TypeError.missingParameter("nombre", null), 400)
        }
        int maxNameLength = grailsApplication.config.restaurant.dish.maxNameLength
        if (name.toString().trim().size() > maxNameLength) {
            throw new MenuValidationException(TypeError.incorrectFormat("nombre", "máximo ${maxNameLength} caracteres", null), 400)
        }
        if (!StringUtils.onlyLettersAndSpaces(name?.toString())) {
            throw new MenuValidationException(TypeError.incorrectFormat("nombre", "solo letras y espacios", null), 400)
        }
    }

    private void validateMenuTypeUuid(def uuid) {
        if (!uuid || uuid.toString().trim().size() != 32) {
            throw new MenuValidationException(TypeError.incorrectFormat("uuid", "UUID de 32 caracteres", null), 400)
        }
        if (!StringUtils.isValidUuid(uuid?.toString())) {
            throw new MenuValidationException(TypeError.incorrectFormat("uuid", "UUID alfanumérico", null), 400)
        }
    }
    
    @Secured(['isAuthenticated()'])
    def listTypes() {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Listado de tipos de menú", "Entrada al servicio", "params: ${params}")
        def auth = springSecurityService.principal
        def isChef = auth?.authorities*.authority?.contains("ROLE_CHEF")
        def serviceResponse = menuService.listTypes(isChef)
        return respond(serviceResponse.resp, status: serviceResponse.status)
    }

    @Secured(['isAuthenticated()'])
    def listSubmenusByParent() {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        def auth = springSecurityService.principal
        def isChef = auth?.authorities*.authority?.contains("ROLE_CHEF")
        Log.logger(Log.INFO, logId, "Listado de submenús", "Entrada al servicio", "params: ${params}")
        if (!params.uuid || params.uuid?.size() != 32) {
            Log.logger(Log.WARN, logId, "Listado de submenús", "UUID inválido", "uuid: ${params.uuid}")
            return respond([success: false, message: "El UUID es inválido"], status: 400)
        }
        if (!StringUtils.isValidUuid(params.uuid?.toString())) {
            return respond([success: false, message: "El Uuid debe ser alfanumérico"], status: 400)
        }
        def serviceResponse = menuService.listSubmenusByParent(params.uuid, isChef)
        return respond(serviceResponse.resp, status: serviceResponse.status)
    }

    @Secured(['ROLE_CHEF'])
    def newType() {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Nuevo tipo de menú", "Entrada al servicio", "params: ${params}")
        try {
            def data = request.JSON
            def name = extractAndValidateMenuTypeName(data)
            if (menuTypeNameExists(name)) {
                return respondNameExists(name, logId)
            }
            def serviceResponse = menuService.newType(name, data.parentType, data.startTime, data.endTime)
            return respond(serviceResponse.resp, status: serviceResponse.status)
        } catch (MenuValidationException e) {
            return respond(e.resp, status: e.status)
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Nuevo tipo de menú", "Error de validación", "error: ${e.class.simpleName} | message: ${e.getMessage()}")
            return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
        }
    }

    @Secured(['ROLE_CHEF'])
    def editType() {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Editar tipo de menú", "Entrada al servicio", "params: ${params}")
        try {
            def data = request.JSON
            def name = extractAndValidateMenuTypeName(data)
            Log.logger(Log.INFO, logId, "Editar tipo de menú", "Validando uuid", "uuid: ${params.uuid}")
            validateMenuTypeUuid(params.uuid)
            def type = findMenuTypeOrRespond(params.uuid, logId)
            if (!type) return
            if (name != type.name && menuTypeNameExists(name)) {
                return respondNameExists(name, logId)
            }
            def serviceResponse = menuService.editType(name, params.uuid, data.startTime, data.endTime)
            return respond(serviceResponse.resp, status: serviceResponse.status)
        } catch (MenuValidationException e) {
            return respond(e.resp, status: e.status)
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Editar tipo de menú", "Error de validación", "error: ${e.class.simpleName} | message: ${e.getMessage()}")
            return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
        }
    }

    @Secured(['isAuthenticated()'])
    def typeInfo() {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Info tipo de menú", "Entrada al servicio", "params: ${params}")
        if (!params.uuid || params.uuid?.size() != 32) {
            Log.logger(Log.WARN, logId, "Info tipo de menú", "UUID inválido", "uuid: ${params.uuid}")
            return respond([success: false, message: "El UUID es inválido"], status: 400)
        }
        def serviceResponse = menuService.typeInfo(params.uuid)
        return respond(serviceResponse.resp, status: serviceResponse.status)
    }

    @Secured(['ROLE_CHEF'])
    def editTypeStatus() {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Editar estado tipo de menú", "Entrada al servicio", "params: ${params}")
        try {
            findMenuTypeOrRespond(params.uuid, logId)
            def serviceResponse = menuService.editTypeStatus(params.status, params.uuid)
            return respond(serviceResponse.resp, status: serviceResponse.status)
        } catch (MenuValidationException e) {
            return respond(e.resp, status: e.status)
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Editar tipo de status", "Error de validación", " message: ${e.getMessage()}")
            return respond([success: false, message: "Error interno del servidor."], status: 500)
        }
    }

    @Secured(['isAuthenticated()'])
    def paginateTypes() {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Paginación tipos de menú", "Entrada al servicio", "params: ${params}")
        def errorMsg = MenuValidationUtils.validatePaginateTypesParams(params, logId, grailsApplication)
        if (errorMsg) {
            return respond([success: false, message: errorMsg], status: 400)
        }
        def sortResult = RestaurantConstants.validateSortColumn(params.orderColumn?.toString()?.trim())
        if (sortResult.warning && sortResult.warning instanceof String && sortResult.warning.trim()) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "orderColumn inválido", "orderColumn: ${params.orderColumn}")
            return respond([success: false, message: sortResult.warning], status: 400)
        }
        params.orderColumn = sortResult.column
        Log.logger(Log.INFO, logId, "Paginación tipos de menú", "Parámetros validados correctamente. Llamando a menuService.paginateTypes", "params: ${params}")
        def serviceResponse = menuService.paginateTypes(params)
        return respond([success: true, data: serviceResponse], status: 200)
    }
}
