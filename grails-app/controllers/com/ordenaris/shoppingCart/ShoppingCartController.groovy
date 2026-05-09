package com.ordenaris.shoppingCart
import grails.rest.*
import grails.converters.*
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.SpringSecurityService
import com.ordenaris.Log
import com.ordenaris.TypeError

@Secured(['isAuthenticated()'])
class ShoppingCartController {
	static responseFormats = ['json']
    def shoppingCartService
    SpringSecurityService springSecurityService
    
    def listOrderShoppingCart(){
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Listado de carritos.", "Inicia Solicitud.", "params: $params")
        def serviceResponse = shoppingCartService.listOrderShoppingCart(params, logId) 
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def getCartByUser(){
        def auth = springSecurityService.principal
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Consultar carrito de compras.", "Inicia Solicitud.", "params: $params")
        if (!auth.id) {
            Log.logger(Log.WARN, logId, "Consultar carrito de compras.", "No se ha iniciado sesion.", "params: $params")
            return respond(TypeError.noPermissions(logId, response))
        }
        def serviceResponse = shoppingCartService.getCartByUser(params, auth, logId) 
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def newOrderShoppingCart(){
        def auth = springSecurityService.principal
        def data = request.JSON
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Crear carrito de compras.", "Inicia Solicitud.", "data: ${data}")
        for (item in data){
                if (!item.dishUuid) {
                    Log.logger(Log.ERROR, logId, "Crear carrito de compras.", "No se recibe el uuid del platillo.", "data: ${data}")
                    return respond(TypeError.missingParameter("platillo", logId, response))
                }
                if (!item.quantityDish || item.quantityDish <= 0) {
                    Log.logger(Log.ERROR, logId, "Crear carrito de compras.", "El numero de platillos no puede ser menor a 0 o ser 0.", "data: ${data}")
                    return respond(TypeError.incorrectFormat("${item.quantityDish}", "cantidad > 0", logId, response))
                }
                if (item.quantityDish > 5) {
                    Log.logger(Log.ERROR, logId, "Crear carrito de compras.", "No se puede rebasar el maximo de 5 platillos.", "data: ${data}")
                    return respond(TypeError.incorrectFormat("${item.quantityDish}", "cantidad <= 5", logId, response))   
                }
        }
        def serviceResponse = shoppingCartService.newOrderShoppingCart(data, auth, logId) 
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def editStatusShoppingCart(){
        def data = params
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Editar estatus del carrito.", "Inicia Solicitud.", "json: $data")
        if (!data.uuidSC) {
            Log.logger(Log.ERROR, logId, "Editar estatus del carrito.", "No viene el carrito.", "json: $data")
            return respond(TypeError.missingParameter("Carrito de compras", logId, response))
        }
        def orderTime = request.JSON.orderTime ?: null
        def serviceResponse = shoppingCartService.editStatusShoppingCart(data, request.JSON.commentUser, orderTime, logId) 
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def addItemShoppingCart(){
        def auth = springSecurityService.principal
        def requestBody = request.JSON
        def pathParams = params
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Agregar nuevo platillo.", "Inicia Solicitud.", "json: $requestBody")
        if (!requestBody.dishUuid) {
            Log.logger(Log.ERROR, logId, "Agregar nuevo platillo.", "No se ha enviado el uuid del platillo.", "json: $requestBody")
            return respond(TypeError.missingParameter("platillo", logId, response))
        }
        if (!requestBody.quantityDish || requestBody.quantityDish <= 0) {
            Log.logger(Log.ERROR, logId, "Agregar nuevo platillo.", "No se puede agregar ese numero de platillos.", "json: $requestBody")
            return respond(TypeError.incorrectFormat("${requestBody.quantityDish}", "cantidad > 0", logId, response))
        }
        if (requestBody.quantityDish > 5) {
            Log.logger(Log.ERROR, logId, "Agregar nuevo platillo.", "No se puede pedir una mayor a 5 platillos por orden.", "json: $requestBody")
            return respond(TypeError.incorrectFormat("${requestBody.quantityDish}", "cantidad <= 5", logId, response))
        }
        def serviceResponse = shoppingCartService.addItemShoppingCart(requestBody, pathParams, auth, logId) 
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def addNumberDish(){
        def requestBody = request.JSON
        def pathParams = params
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Sumar cantidad del platillo.", "Inicia Solicitud.", "json: $requestBody, params: $pathParams")
        if(!requestBody){
            Log.logger(Log.ERROR, logId, "Sumar cantidad del platillo.", "Faltan los parametros.", "json: $requestBody, params: $pathParams")
            return respond(TypeError.missingParameter("platillo", logId, response))
        }
        if (!pathParams.uuidItem) {
            Log.logger(Log.ERROR, logId, "Sumar cantidad del platillo.", "Faltan el uuid del platillo.", "json: $requestBody, params: $pathParams")
            return respond(TypeError.missingParameter("platillo", logId, response))
        }
        if (!requestBody.quantityDish || requestBody.quantityDish <= 0) {
            Log.logger(Log.ERROR, logId, "Sumar cantidad del platillo.", "No se puede agregar ese numero de platillos.", "json: $requestBody, params: $pathParams")
            return respond(TypeError.incorrectFormat("${requestBody.quantityDish}", "cantidad > 0", logId, response))
        }
        if (requestBody.quantityDish > 5) {
            Log.logger(Log.ERROR, logId, "Sumar cantidad del platillo.", "No se puede pedir una mayor a 5 platillos por orden.", "json: $requestBody, params: $pathParams")
            return respond(TypeError.incorrectFormat("${requestBody.quantityDish}", "cantidad <= 5", logId, response))
        }
        def serviceResponse = shoppingCartService.addNumberDish(requestBody, pathParams, logId) 
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def restNumberDish(){
        def requestBody = request.JSON
        def pathParams = params
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Restar cantidad del platillo.", "Inicia Solicitud.", "json: $requestBody, params: $pathParams")    
        if(!requestBody){
            Log.logger(Log.ERROR, logId, "Restar cantidad del platillo.", "Faltan datos del platillo.", "json: $requestBody, params: $pathParams")
            return respond(TypeError.missingParameter("platillo", logId, response))
        }
        if (!pathParams.uuidItem) {
            Log.logger(Log.ERROR, logId, "Restar cantidad del platillo.", "No llega el platillo.", "json: $requestBody, params: $pathParams")
            return respond(TypeError.missingParameter("platillo", logId, response))
        }
        if (!requestBody.quantityDish || requestBody.quantityDish <= 0) {
            Log.logger(Log.ERROR, logId, "Restar cantidad del platillo.", "No se puede restar una cantidad 0 o negativo.", "json: $requestBody, params: $pathParams")
            return respond(TypeError.incorrectFormat("${requestBody.quantityDish}", "cantidad > 0", logId, response))
        }
        
        def serviceResponse = shoppingCartService.restNumberDish(requestBody, pathParams, logId) 
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def deleteItemShoppingCart(){
        def data = params
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Eliminar platillo del carrito.", "Inicia Solicitud.", "json: $data")
        if (!data.uuidSC) {
            Log.logger(Log.ERROR, logId, "Eliminar platillo del carrito.", "Falta el UUID del carrito de compras.", "json: $data")
            return respond(TypeError.missingParameter("carrito de compras", logId, response))
        }
        if (!data.uuidItem) {
            Log.logger(Log.ERROR, logId, "Eliminar platillo del carrito.", "Falta el UUID del platillo.", "json: $data")
            return respond(TypeError.missingParameter("platillo", logId, response))
        }
        def serviceResponse = shoppingCartService.deleteItemShoppingCart(data, logId) 
        return respond(serviceResponse.data, status: serviceResponse.status)
    }
}
