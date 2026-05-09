package com.ordenaris.order
import com.ordenaris.order.CustomerOrder
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.SpringSecurityService
import grails.rest.*
import grails.converters.*
import java.time.LocalTime
import com.ordenaris.Log
import com.ordenaris.TypeError

@Secured(['isAuthenticated()'])
class OrdersModuleController {
	static responseFormats = ['json']
	def orderModuleService
    def scheduleService
    SpringSecurityService springSecurityService    
    private static final List<String> VALID_STATUSES = ["Cancelled", "Preparing", "Queue", "Finished"]
    
    
    @Secured(['ROLE_CHEF', 'ROLE_ADMIN'])
    def listOrderByChef() {
        def auth = springSecurityService.principal
        if (!auth || !auth.id) {
            return respond([success: false, message: "Usuario no autenticado"], status: 401)
        }
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Listar ordenes del chef.", "Inicia Solicitud.", "chefId: ${auth.id}")
        def serviceResponse = orderModuleService.listOrderByChef(auth.id, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }


    def listOrders(){
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Consultar las ordenes.", "Inicia Solicitud.", "params: $params")
        def serviceResponse = orderModuleService.listOrders(params, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['ROLE_EMPLOYEE', 'ROLE_ADMIN', 'ROLE_FINANCE'])
    def listOrdersByUser(){
        def auth = springSecurityService.principal
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Consultar ordenes usuario.", "Inicia Solicitud.", "params: $params")
        if (!auth.id) {
            Log.logger(Log.INFO, logId, "Consultar ordenes usuario.", "No se ha encontrado al usuario.", "params: $params")
            return respond(TypeError.missingParameter("usuario", logId, response))
        }
        def serviceResponse = orderModuleService.listOrdersByUser(params, auth.id, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['isAuthenticated()'])
    def newOrder(){
        def data = request.JSON.order
        def auth = springSecurityService.principal  
        def orderTime = request.JSON.orderTime
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Crear nueva orden.", "Inicia Solicitud.", "data: $data")
        for (item in data){
            if(!item){
                Log.logger(Log.ERROR, logId, "Crear nueva orden.", "No se recibe nada.", "data: $data")
                return respond(TypeError.missingParameter("item", logId, response))
            }
            if(!item.dishUuid){
                Log.logger(Log.ERROR, logId, "Crear nueva orden.", "No se recibe el uuid del platillo.", "data: $data")
                return respond(TypeError.missingParameter("platillo de la orden", logId, response))
            }
            if(!item.quantityDish || item.quantityDish <= 0){
                Log.logger(Log.ERROR, logId, "Crear nueva orden.", "No se pueden ingresar esas cantidades en la cantidad del platillo.", "data: $data")
                return respond(TypeError.incorrectFormat("cantidad de platillos", "numero > a 0", logId, response))
            }
            if(item.quantityDish > 5){
                Log.logger(Log.ERROR, logId, "Crear nueva orden.", "No se pueden agregar mas de 5 platillos por orden.", "data: $data")
                return respond(TypeError.incorrectFormat("cantidad de platillos", "numero <= a 5", logId, response))
            }
        }
        def chefAvailable = scheduleService.isAnyChefAvailable(logId).data
        if (!chefAvailable?.data?.isAnyAvailable) {
            Log.logger(Log.ERROR, logId, "Crear nueva orden.", "No se puede crear una orden fuera del horario laboral del chef.", "data: $data")
            return respond(TypeError.resourceNotAvailable("horario del chef", logId, response))
        }
        if (orderTime) {
            try {
                orderTime = LocalTime.parse(orderTime)
            } catch (Exception e) {
                Log.logger(Log.ERROR, logId, "Crear nueva orden.", "Formato de hora invalido.", "orderTime: $orderTime")
                return respond(TypeError.incorrectFormat("Horario de pedido", "formato de hora (HH:mm:ss)", logId, response))
            }
        }

        def serviceResponse = orderModuleService.newOrder(data, auth, orderTime, request.JSON.commentUser, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }
    
    @Secured(['ROLE_EMPLOYEE', 'ROLE_ADMIN', 'ROLE_FINANCE'])
    def addDishOrder(){
        def pathParams = params
        def requestBody = request.JSON
        def auth = springSecurityService.principal
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Añadiendo nuevo platillo.", "Inicia Solicitud.", "json: $requestBody")  
        if (!pathParams.uuidOrder) {
            Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "Falta el UUID de la orden.", "json: $requestBody")
            return respond(TypeError.missingParameter("orden", logId, response))
        }
        if (!requestBody.uuidDish) {
            Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "Falta el ID del nuevo platillo.", "json: $requestBody")
            return respond(TypeError.missingParameter("platillo", logId, response))
        }
        if (!requestBody.quantityDish || requestBody.quantityDish < 1) {
            Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "El numero de platillos no puede ser menor a 0 o ser 0.", "json: $requestBody")
            return respond(TypeError.incorrectFormat("cantidad de platillos", "numero > a 0", logId, response))
        }
        if (requestBody.quantityDish > 5) {
            Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "El numero de platillos no puede ser mayor a 5.", "json: $requestBody")
            return respond(TypeError.incorrectFormat("cantidad de platillos", "numero <= a 5", logId, response))
        }
        def serviceResponse = orderModuleService.addDishOrder(pathParams, requestBody, auth, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['ROLE_EMPLOYEE', 'ROLE_ADMIN', 'ROLE_FINANCE'])
    def editOrder(){
        def pathParams = params
        def requestBody = request.JSON
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Editar orden.", "Inicia Solicitud.", "json: $requestBody")
        if (!pathParams.uuidOrder) {
            Log.logger(Log.ERROR, logId, "Editar orden.", "Falta el UUID de la orden.", "json: $requestBody")
            return respond(TypeError.missingParameter("orden", logId, response))
        }
        if (!pathParams.uuidItem) {
            Log.logger(Log.ERROR, logId, "Editar orden.", "Falta el UUID del platillo en la orden.", "json: $requestBody")
            return respond(TypeError.missingParameter("platillo en orden", logId, response))
        }
        if (!requestBody) {
            Log.logger(Log.ERROR, logId, "Editar orden.", "Faltan los datos para editar la orden.", "json: $requestBody")
            return respond(TypeError.missingParameter("datos de orden", logId, response))
        }
        if (!requestBody.uuidDish) {
            Log.logger(Log.ERROR, logId, "Editar orden.", "Falta el UUID del nuevo platillo.", "json: $requestBody")
            return respond(TypeError.missingParameter("nuevo platillo en orden", logId, response))
        }
        if (!requestBody.quantityDish || requestBody.quantityDish < 1) {
            Log.logger(Log.ERROR, logId, "Editar orden.", "El numero de platillos no puede ser menor a 0 o ser 0.", "json: $requestBody")
            return respond(TypeError.incorrectFormat("numero de platillos", "numero > a 0", logId, response))
        }
        if (requestBody.quantityDish > 5) {
            Log.logger(Log.ERROR, logId, "Editar orden.", "El numero de platillos no puede ser mayor a 5.", "json: $requestBody")
            return respond(TypeError.incorrectFormat("numero de platillos", "numero <= a 5", logId, response))
        }
       
        def serviceResponse = orderModuleService.editOrder(pathParams, requestBody, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }
    
    @Secured(['isAuthenticated()'])
    def editOrderStatus(){
        def data = params
        def requestBody = [:]
        def completedTime = null
        def auth = springSecurityService.principal
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Editar el estatus.", "Inicia Solicitud.", "data: $data")

        if (request.contentLength > 0) {
            try {
                requestBody = request.JSON ?: [:]
            } catch (Exception e) {
                Log.logger(Log.ERROR, logId, "Editar el estatus.", "Cuerpo JSON invalido.", "data: $data")
                return respond([success: false, message: "Cuerpo JSON invalido."], status: 400)
            }
        }

        if (!data.uuidOrder) {
            Log.logger(Log.ERROR, logId, "Editar el estatus.", "Falta el UUID de la orden.", "data: $data")
            return respond(TypeError.missingParameter("orden", logId, response))
        }
        if (!data.status) {
            Log.logger(Log.ERROR, logId, "Editar el estatus.", "Falta el nuevo estado de la orden.", "data: $data")
            return respond(TypeError.missingParameter("estatus", logId, response))
        }
        if (!(data.status in VALID_STATUSES)) {
            Log.logger(Log.ERROR, logId, "Editar el estatus.", "Estado de orden invalido.", "data: $data")
            return respond(TypeError.incorrectFormat("estatus", "${VALID_STATUSES}", logId, response))
        }
        if (data.status == "Finished" ) {
            if (!requestBody.completedTime) {
                    Log.logger(Log.ERROR, logId, "Editar el estatus.", "El horario de entrega es obligatorio para finalizar la orden.", "data: $data")
                    return respond(TypeError.missingParameter("Horario de entrega", logId, response))
                }
            try {
                completedTime = LocalTime.parse(requestBody.completedTime)
            } catch (Exception e) {
                return respond(TypeError.incorrectFormat("Horario de entrega", "formato de hora (HH:mm:ss)", logId, response))
            }
        }
        
        data.auth = auth
        def serviceResponse = orderModuleService.editOrderStatus(data, completedTime, requestBody.commentChef, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['ROLE_CHEF', 'ROLE_ADMIN'])
    def rejectDish() {
        def uuidOrder = params.uuidOrder
        def uuidItem = params.uuidItem
        def auth = springSecurityService.principal
        def data = request.JSON

        if (!uuidOrder || uuidOrder.size() != 32) {
            return respond([success: false, message: "UUID de orden inválido"], status: 400)
        }

        if (!uuidItem || uuidItem.size() != 32) {
            return respond([success: false, message: "UUID de platillo inválido"], status: 400)
        }

        if (!data.reason || !data.reason.trim()) {
            return respond([success: false, message: "Debe proporcionar una razón del rechazo"], status: 400)
        }

        def serviceResponse = orderModuleService.rejectDish(uuidOrder, uuidItem, data.reason, auth)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['permitAll'])
    def listRejections() {
        def auth = springSecurityService.principal
        def serviceResponse = orderModuleService.listRejections(auth)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['permitAll'])
    def rejectionInfo() {
        def rejectionUuid = params.rejectionUuid

        if (!rejectionUuid || rejectionUuid.size() != 32) {
            return respond([success: false, message: "UUID de rechazo inválido"], status: 400)
        }

        def serviceResponse = orderModuleService.rejectionInfo(rejectionUuid)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['permitAll'])
    def approveRejection() {
        def rejectionUuid = params.rejectionUuid
        def auth = springSecurityService.principal
        if (!rejectionUuid || rejectionUuid.size() != 32) {
            return respond([success: false, message: "UUID de rechazo inválido"], status: 400)
        }

        def serviceResponse = orderModuleService.approveRejection(rejectionUuid, auth)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['permitAll'])
    def cancelRejection() {
        def rejectionUuid = params.rejectionUuid
        def auth = springSecurityService.principal
        if (!rejectionUuid || rejectionUuid.size() != 32) {
            return respond([success: false, message: "UUID de rechazo inválido"], status: 400)
        }

        def serviceResponse = orderModuleService.cancelRejection(rejectionUuid, auth)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }
}
