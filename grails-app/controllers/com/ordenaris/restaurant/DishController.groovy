package com.ordenaris.restaurant

import com.ordenaris.Log
import com.ordenaris.TypeError
import grails.plugin.springsecurity.SpringSecurityService
import grails.plugin.springsecurity.annotation.Secured
import grails.converters.JSON
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import grails.core.GrailsApplication


class DishController {   
        
        static responseFormats = ['json', 'xml'] 
        
        def dishService
        GrailsApplication grailsApplication


        @Secured(['ROLE_EMPLOYEE', 'ROLE_CHEF', 'ROLE_ADMIN'])
        def listActiveDishes() {
            def response = dishService.listActiveDishes()
            return respond(response.data, status: response.status)
        }

        @Secured(['ROLE_CHEF', 'ROLE_ADMIN'])
        def listAllDishes() {
            def response = dishService.listAllDishes()
            return respond(response.data, status: response.status)
        }

        @Secured(['ROLE_CHEF'])
        def newDish() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Crear platillo", "Inicia solicitud", "params: ${params}, json: ${request.JSON}")
            try {
                def data = extractNewDishDataFromRequest(request, logId)
                validateNewDishData(data)
                def dishParams = buildNewDishParams(data)
                MultipartFile imageFile = extractImageFileFromRequest(request, logId, false, "Crear platillo")
                def response = dishService.newDish(dishParams, imageFile)
                return respond(response.data, status: response.status)
            } catch (IllegalArgumentException e) {
                Log.logger(Log.ERROR, logId, "Error en newDish", e.getMessage(), "params: ${request.JSON}")
                return respond([success: false, message: "Datos inválidos para crear el platillo."], status: 400)
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en newDish", e.getMessage(), "params: ${request.JSON}")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }

        private def extractNewDishDataFromRequest(request, String logId) {
            if (!(request instanceof MultipartHttpServletRequest)) {
                return request.JSON
            }

            def rawData = request.getParameter('data')
            if (rawData) {
                try {
                    return JSON.parse(rawData)
                } catch (Exception e) {
                    Log.logger(Log.WARN, logId, "Crear platillo", "JSON inválido en multipart data", "error: ${e.getMessage()}")
                    throw new IllegalArgumentException("Formato inválido para el campo data")
                }
            }

            return [
                name: request.getParameter('name'),
                menuType: request.getParameter('menuType'),
                availableDate: parseMultipartScalarValue(request.getParameter('availableDate')),
                cost: parseMultipartScalarValue(request.getParameter('cost')),
                description: request.getParameter('description'),
                availableDishes: parseMultipartScalarValue(request.getParameter('availableDishes')),
                imageUrl: request.getParameter('imageUrl')
            ]
        }

        private def parseMultipartScalarValue(value) {
            def trimmedValue = value?.trim()
            if (!trimmedValue) {
                return value
            }

            try {
                JSON.parse(trimmedValue)
            } catch (ignored) {
                return value
            }
        }

        private void validateNewDishData(def data) {
            def result = DishValidationUtils.validateNewDishData(data, grailsApplication)
            validateDishValidationResult(result)
        }

        private void validateDishValidationResult(def result) {
            if (result instanceof Map && result.success == false) {
                throw new IllegalArgumentException(result.message?.toString() ?: "Datos inválidos")
            }
        }

        private Map buildNewDishParams(def data) {
            return [
                sourceDishUuid: params.uuid?.toString()?.trim(),
                name: data.name.toString().trim(),
                menuType: data.menuType,
                availableDate: getAvailableDate(data.availableDate),
                cost: data.cost, 
                description: data.description.toString().trim(),
                availableDishes: getAvailableDishes(data.availableDishes),
                imageUrl: data.imageUrl
            ]
        }

        private Date getAvailableDate(def availableDate) {
            def normalizedAvailableDate = (availableDate != null && availableDate.toString().trim() && availableDate.toString().trim() != '-1') ? availableDate : null
            if (normalizedAvailableDate == null) {
                return null
            }

            def parsedDate = DishValidationUtils.validateAndParseAvailableDate(normalizedAvailableDate)
            validateDishValidationResult(parsedDate)
            return parsedDate as Date
        }

        private Integer getAvailableDishes(def availableDishes) {
            return availableDishes != null ? DishValidationUtils.validateAvailableDishes(availableDishes, grailsApplication) : null
        }

        private Integer getCost(def cost) {
            return DishValidationUtils.convertAndValidateCost(cost, grailsApplication)
        }

        private Integer parseStatusParam(def statusParam, boolean required = false) {
            return DishValidationUtils.parseStatusParam(statusParam, required)
        }

        @Secured(['ROLE_CHEF'])
        def incrementAvailableDishes() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Incrementar disponibilidad platillo", "Inicia solicitud", "params: ${params}, json: ${request.JSON}")
            try {
                def data = request.JSON
                def validationError = validateIncrementQuantity(data, grailsApplication)
                if (validationError) {
                    return respond([success: false, message: validationError], status: 400)
                }
                Integer quantity = data.quantity.toInteger()
                def response = dishService.incrementAvailableDishes(params.uuid?.toString().trim(), quantity)
                def resp = formatIncrementResponse(response.data)
                return respond(resp, status: response.status)
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en incrementAvailableDishes", e.getMessage(), "params: { uuid: ${params.uuid}, quantity: ${request.JSON?.quantity} }")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }

        private String validateIncrementQuantity(def data, GrailsApplication grailsApplication) {
            if (!data.quantity) {
                return "La cantidad a aumentar es obligatoria"
            }
            if (data.quantity instanceof String && !data.quantity.onlyNumbers()) {
                return "La cantidad debe ser un número"
            }
            Integer quantity
            try {
                quantity = data.quantity.toInteger()
            } catch (e) {
                return "La cantidad debe ser un número válido"
            }
            def minQuantity = grailsApplication.config.restaurant.dish.minQuantity
            if (quantity < minQuantity) {
                return "La cantidad debe ser mayor a 0"
            }
            return null
        }

        private Map formatIncrementResponse(Map resp) {
            if (resp?.data) {
                if (resp.data instanceof Map && resp.data.message) {
                    resp.message = resp.data.message?.toString()
                    resp.remove('data')
                } else if (resp.data instanceof String) {
                    resp.message = resp.data.toString()
                    resp.remove('data')
                }
            }
            if (resp?.message && resp.message instanceof Map) {
                resp.message = resp.message.toString()
            }
            return resp
        }
    
        @Secured(['isAuthenticated()'])
        def dishInfo() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Info platillo", "Inicia solicitud", "params: { uuid: ${params.uuid}, status: ${params.status} }")
            if (!params.uuid || params.uuid.toString().trim().size() != 32) {
                Log.logger(Log.WARN, logId, "Info platillo", "UUID inválido", "uuid: ${params.uuid}")
                return respond(TypeError.invalidData("uuid", logId), status: 403)
            }
            try {
                def requestedStatus = parseStatusParam(params.status, false)
                def response = dishService.dishInfo(params.uuid?.toString().trim(), requestedStatus)
                return respond(response.data, status: response.status)
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en dishInfo", e.getMessage(), "params: { uuid: ${params.uuid}, status: ${params.status} }")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }

        @Secured(['ROLE_CHEF'])
        def editDish() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Editar platillo", "Inicia solicitud", "params: ${params}, json: ${request.JSON}")
            try {
                if (!params.uuid || !params.uuid.toString().trim()) {
                    return respond([success: false, message: "UUID es requerido"], status: 400)
                }
                if (params.uuid.toString().trim().size() != 32) {
                    return respond([success: false, message: "UUID inválido, debe tener 32 caracteres"], status: 400)
                }
                def data = request.JSON
                validateEditDishData(data)
                def dishParams = buildEditDishParams(data, params.uuid)
                def response = dishService.editDish(dishParams)
                if (response.status == 404) {
                    return respond([success: false, message: "El platillo no existe"], status: 404)
                }
                return respond(response.data, status: response.status)
            } catch (IllegalArgumentException e) {
                Log.logger(Log.ERROR, logId, "Error en editDish", e.getMessage(), "params: ${request.JSON?.toString()}")
                return respond([success: false, message: "Datos inválidos para editar el platillo."], status: 400)
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en editDish", e.getMessage(), "params: ${request.JSON?.toString()}")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }

        private void validateEditDishData(def data) {
            if (data.name) validateDishValidationResult(DishValidationUtils.validateDishName(data.name, grailsApplication))
            if (data.menuType) validateDishValidationResult(DishValidationUtils.validateMenuType(data.menuType))
            if (data.containsKey('cost')) validateDishValidationResult(DishValidationUtils.validateDishCost(data.cost))
            if (data.description) validateDishValidationResult(DishValidationUtils.validateDishDescription(data.description, grailsApplication))
            if (data.availableDate != null) validateDishValidationResult(DishValidationUtils.validateAndParseAvailableDate(data.availableDate))
            if (data.availableDishes != null) DishValidationUtils.validateAvailableDishes(data.availableDishes, grailsApplication)
            if (data.imageUrl != null) validateDishValidationResult(DishValidationUtils.validateImageUrl(data.imageUrl, grailsApplication))
        }

        private Map buildEditDishParams(def data, def uuid) {
            return [
                uuid: uuid?.toString()?.trim(),
                name: data.name != null ? data.name.toString().trim() : null,
                menuType: data.menuType,
                availableDate: data.availableDate != null ? getAvailableDate(data.availableDate) : null,
                cost: data.cost ? DishValidationUtils.convertAndValidateCost(data.cost, grailsApplication) : null,
                description: data.description != null ? data.description.toString().trim() : null,
                availableDishes: data.availableDishes ? DishValidationUtils.validateAvailableDishes(data.availableDishes, grailsApplication) : null,
                imageUrl: data.imageUrl != null ? data.imageUrl.toString().trim() : null
            ]
        }

        @Secured(['ROLE_CHEF'])
        def editDishStatus() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Editar status platillo", "Inicia solicitud", "params: ${params}, json: ${request.JSON}")
            try {
                if (!params.uuid || params.uuid.toString().trim().size() != 32) {
                    Log.logger(Log.WARN, logId, "Editar status platillo", "UUID inválido", "uuid: ${params.uuid}")
                    return respond([success: false, message: "El UUID proporcionado no es válido. Debe tener exactamente 32 caracteres alfanuméricos."], status: 400)
                }
                def statusValue = parseStatusParam(params.status, true)
                Date availableDate = null
                if (statusValue == EntityStatus.ACTIVE && request.JSON?.containsKey('availableDate') && request.JSON.availableDate != null) {
                    def parsedDate = DishValidationUtils.validateAndParseAvailableDate(request.JSON.availableDate)
                    validateDishValidationResult(parsedDate)
                    availableDate = parsedDate as Date
                }
                def response = dishService.editDishStatus(statusValue, params.uuid?.toString().trim(), availableDate)
                return respond(response.data, status: response.status)
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en editDishStatus", e.getMessage(), "params: { uuid: ${params.uuid} }")
                def msg = "Ha ocurrido un error, inténtalo más tarde."
                def statusCode = 500
                if (e.getMessage() == "El platillo no existe o está eliminado") {
                    msg = e.getMessage()
                    statusCode = 404
                }
                return respond([success: false, message: msg], status: statusCode)
            }
        }



        @Secured(['isAuthenticated()'])
        def paginateDishes() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Paginar platillos", "Entrada al servicio", "params: ${params}")
            def errorMsg = validatePaginateParamsCustom(params, logId, grailsApplication)
            if (errorMsg) {
                return respond([success: false, message: errorMsg], status: 400)
            }
            params.orderColumn = getValidatedSortColumn(params.orderColumn, logId)
            if (params.orderColumn == null) {
                return respond([success: false, message: "orderColumn inválido"], status: 400)
            }
            Log.logger(Log.INFO, logId, "Paginar platillos", "Parámetros validados correctamente. Llamando a dishService.paginateDishes", "params: ${params}")
            def serviceResult = dishService.paginateDishes(params)
            return respond(serviceResult.data, status: serviceResult.status)
        }

        private String validatePaginateParamsCustom(params, logId, GrailsApplication grailsApplication) {
            def errorMsg = DishValidationUtils.validatePaginateDishesParams(params, logId, grailsApplication)
            return errorMsg
        }

        private String getValidatedSortColumn(orderColumn, logId) {
            def sortResult = com.ordenaris.restaurant.RestaurantConstants.validateSortColumn(orderColumn?.toString()?.trim())
            if (sortResult.warning && sortResult.warning instanceof String && sortResult.warning.trim()) {
                Log.logger(Log.WARN, logId, "Paginar platillos", "orderColumn inválido", "orderColumn: ${orderColumn}")
                return null
            }
            return sortResult.column
        }

        @Secured(['isAuthenticated()'])
        def topDishesChart() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Top platillos más vendidos", "Inicia solicitud", "params: { days: ${params.days}, limit: ${params.limit} }")
            try {
                if (params.days && !StringUtils.onlyNumbers(params.days?.toString())) {
                    return respond([success: false, message: "El número de días debe ser un número"], status: 400)
                }
                if (params.limit && !StringUtils.onlyNumbers(params.limit?.toString())) {
                    return respond([success: false, message: "El límite debe ser un número"], status: 400)
                }
                Integer days = params.days ? params.days.toInteger() : 7
                Integer limit = params.limit ? params.limit.toInteger() : 10
                if (days < 1) return respond([success: false, message: "El número de días debe ser mayor a 0"], status: 400)
                if (limit < 1) return respond([success: false, message: "El límite debe ser mayor a 0"], status: 400)
                def response = dishService.getTopDishesChart(days, limit)
                return respond(response.data, status: response.status)
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en topDishesChart", e.getMessage(), "params: { days: ${params.days}, limit: ${params.limit} }")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }


        @Secured(['ROLE_CHEF'])
        def uploadDishImage() {
            String logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Subida de imagen platillo", "Inicia solicitud", "params: { uuid: ${params.uuid} }")
            try {
                if (!params.uuid || params.uuid.toString().trim().size() != 32) {
                    Log.logger(Log.WARN, logId, "Info platillo", "UUID inválido", "uuid: ${params.uuid}")
                    return respond([success: false, message: "Access is denied"], status: 403)
                }
                MultipartFile file = extractImageFileFromRequest(request, logId)
                if (!file) {
                    return respond([success: false, message: "El archivo de imagen es requerido"], status: 400)
                }
                def response = dishService.saveDishImageByUuid(params.uuid.toString().trim(), file)
                return respond(response.data, status: response.status)
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Subida de imagen platillo", "Error al subir imagen", e.getMessage())
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }

        private MultipartFile extractImageFileFromRequest(request, logId, boolean required = true, String operation = "Subida de imagen platillo") {
            if (request instanceof MultipartHttpServletRequest) {
                def file = ((MultipartHttpServletRequest) request).getFile('image')
                Log.logger(Log.INFO, logId, operation, "Archivo recibido", "filename: ${file?.originalFilename}, size: ${file?.size}, contentType: ${file?.contentType}")
                if (!file || file.empty) {
                    if (required) {
                        Log.logger(Log.ERROR, logId, operation, "Archivo de imagen es requerido o vacío")
                    }
                    return null
                }
                return file
            } else {
                if (required) {
                    Log.logger(Log.ERROR, logId, operation, "Request no es MultipartHttpServletRequest")
                }
                return null
            }
        }

        @Secured(['isAuthenticated()'])
        def downloadDishImage() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Descargar imagen platillo", "Inicia solicitud", "params: { fileName: ${params.fileName} }")
            try {
                if (!params.fileName || !params.fileName.toString().trim()) {
                    return respond([success: false, message: "El nombre del archivo es requerido"], status: 400)
                }
                if (!params.fileName.toString().trim().matches(RestaurantConstants.DISH_FILE_NAME_PATTERN)) {
                    return respond([success: false, message: "El nombre del archivo debe ser alfanumérico y tener una extensión válida"], status: 400)
                }
                File imageFile = dishService.resolveDishImageByFileName(params.fileName.toString().trim())
                response.contentType = java.nio.file.Files.probeContentType(imageFile.toPath())
                response.outputStream << imageFile.bytes
                response.outputStream.flush()
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en downloadDishImage", e.getMessage(), "params: { fileName: ${params.fileName} }")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }

        def publicDishImage() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Imagen pública platillo", "Inicia solicitud", "params: { fileName: ${params.fileName} }")
            try {
                if (!params.fileName || !params.fileName.toString().trim()) {
                    return respond([success: false, message: "El nombre del archivo es requerido"], status: 400)
                }
                if (!params.fileName.toString().trim().matches(RestaurantConstants.DISH_FILE_NAME_PATTERN)) {
                    return respond([success: false, message: "El nombre del archivo debe ser alfanumérico y tener una extensión válida"], status: 400)
                }
                File imageFile = dishService.resolveDishImageByFileName(params.fileName.toString().trim())
                response.contentType = java.nio.file.Files.probeContentType(imageFile.toPath())
                response.outputStream << imageFile.bytes
                response.outputStream.flush()
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Imagen pública platillo", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "params: { fileName: ${params.fileName} }")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }

        @Secured(['ROLE_CHEF'])
        def reloadDishImage() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Recargar imagen platillo", "Inicia solicitud", "params: { uuid: ${params.uuid} }")
            try {
                if (!params.uuid || !params.uuid.toString().trim()) {
                    return respond([success: false, message: "El UUID del platillo es requerido"], status: 400)
                }
                MultipartFile file = null
                if (request instanceof MultipartHttpServletRequest) {
                    file = ((MultipartHttpServletRequest) request).getFile('image')
                } else {
                    return respond([success: false, message: "La petición debe ser form-data"], status: 400)
                }
                if (!file || file.empty) {
                    return respond([success: false, message: "El archivo de imagen es requerido"], status: 400)
                }
                def response = dishService.reloadDishImageByUuid(params.uuid.toString().trim(), file)
                Log.logger(Log.INFO, logId, "Recargar imagen platillo", "Respuesta de dishService", "response: ${response}")
                return respond(response.data, status: response.status)
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en reloadDishImage", e.getMessage(), "params: { uuid: ${params.uuid} }")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }

        @Secured(['ROLE_CHEF'])
        def deleteDishImage() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Eliminar imagen platillo", "Inicia solicitud", "params: { uuid: ${params.uuid} }")
            try {
                if (!params.uuid || !params.uuid.toString().trim()) {
                    return respond([success: false, message: "El UUID del platillo es requerido"], status: 400)
                }
                def response = dishService.deleteDishImageByUuid(params.uuid.toString().trim())
                Log.logger(Log.INFO, logId, "Eliminar imagen platillo", "Respuesta de dishService", "response: ${response}")
                return respond(response?.resp ?: [success: true, message: "Imagen eliminada"], status: response?.status ?: 200)
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en deleteDishImage", e.getMessage(), "params: { uuid: ${params.uuid} }")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }

    @Secured(['isAuthenticated()'])
    def topSellingDishes() {
        if (params.limit) {
            if (!StringUtils.onlyNumbers(params.limit?.toString())) {
                return respond([success: false, message: "El límite debe ser un número"], status: 400)
            }
        }
        def defaultLimit = grailsApplication.config.restaurant.dish.topDishesChartLimit
        def limit = params.limit ? params.limit.toInteger() : defaultLimit
        if (limit < 1) {
            return respond([success: false, message: "El límite debe ser mayor a 0"], status: 400)
        }
        def response = dishService.getTopSellingDishes(limit)
        return respond(response.data, status: response.status)
    }

    @Secured(['isAuthenticated()'])
    def dishRankingByRating() {
        if (params.limit) {
            if (!StringUtils.onlyNumbers(params.limit?.toString())) {
                return respond([success: false, message: "El límite debe ser un número"], status: 400)
            }
        }
        def defaultLimit = grailsApplication.config.restaurant.dish.topDishesChartLimit
        def limit = params.limit ? params.limit.toInteger() : defaultLimit
        if (limit < 1) {
            return respond([success: false, message: "El límite debe ser mayor a 0"], status: 400)
        }
        def response = dishService.getDishRankingByRating(limit)
        return respond(response.data, status: response.status)
    }

        @Secured(['ROLE_EMPLOYEE', 'ROLE_CHEF', 'ROLE_ADMIN'])
        def getDishImage() {
            def logId = UUID.randomUUID().toString().replaceAll('-', '')
            Log.logger(Log.INFO, logId, "Obtener imagen platillo", "Inicia Solicitud", "params: { uuid: ${params.uuid} }")
            if (!params.uuid || !params.uuid.toString().trim()) {
                return respond([success: false, message: "El UUID del platillo es requerido"], status: 400)
            }
            try {
                File imageFile = dishService.resolveDishImageByUuidPublic(params.uuid.toString().trim())
                if (!imageFile || !imageFile.exists()) {
                    return respond([success: false, message: "Imagen no encontrada"], status: 404)
                }
                response.contentType = java.nio.file.Files.probeContentType(imageFile.toPath())
                response.outputStream << imageFile.bytes
                response.outputStream.flush()
            } catch (e) {
                Log.logger(Log.ERROR, logId, "Error en getDishImage", e.getMessage(), "params: { uuid: ${params.uuid} }")
                return respond([success: false, message: "Ha ocurrido un error, inténtalo más tarde."], status: 500)
            }
        }
}
