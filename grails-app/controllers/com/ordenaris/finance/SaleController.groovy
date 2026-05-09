package com.ordenaris.finance

import grails.rest.*
import grails.converters.*
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.SpringSecurityService
import com.ordenaris.Log
import com.ordenaris.Constants
import com.ordenaris.TypeError

@Secured(['isAuthenticated()'])
class SaleController {
    static responseFormats = ['json', 'xml']
    def saleService
    SpringSecurityService springSecurityService

    @Secured(['ROLE_FINANCE','ROLE_ADMIN'])
    def listDebtors() {
        def query = params.query
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Listado de deuda de usuarios.", "Inicia Solicitud.")
        def serviceResponse = saleService.listDebtUsers(logId, query)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['ROLE_FINANCE','ROLE_ADMIN'])
    def getSalesPaid(){
        def query  = params.query
        def column = params.column ?: "lastUpdated"
        def start  = params.start
        def end    = params.end
        def page   = params.page ?: "0"
        def size   = params.size ?: "10"
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Listado de ingresos.", "Inicia Solicitud.")
        if(!page.isInteger()) {
            return respond(TypeError.incorrectFormat("página", "valor numérico", logId, response))
        }
        if (!size.isInteger()) {
            return respond(TypeError.incorrectFormat("tamaño", "valor numérico", logId, response))
        }
        if (!(size.toInteger() in Constants.ALLOWED_PAGE_SIZES)) {
            return respond(TypeError.incorrectFormat("tamaño", Constants.ALLOWED_PAGE_SIZES.toString(), logId, response))
        }
        if (!(column in Constants.ALLOWED_INCOME_SORT_COLUMNS)) {
            return respond(TypeError.incorrectFormat("columna", Constants.ALLOWED_INCOME_SORT_COLUMNS.toString(), logId, response))
        }
        if (start && !end) {
            return respond(TypeError.missingParameter("fecha fin", logId, response))
        }
        if (!start && end) {
            return respond(TypeError.missingParameter("fecha inicio", logId, response))
        }
        if (start && end) {
            if (!start.isLong() || start.size() != 13) {
                return respond(TypeError.incorrectFormat("fecha inicio", "valor numérico (milisegundos)", logId, response))
            }
            if (!end.isLong() || end.size() != 13) {
                return respond(TypeError.incorrectFormat("fecha fin", "valor numérico (milisegundos)", logId, response))
            }
            if (new Date(end as long).before(new Date(start as long))) {
                return respond(TypeError.invalidData("fechas inicio/fin", logId, response))
            }
        }
        def serviceResponse = saleService.getSalesPaid(logId, query, column, start, end, page.toInteger(), size.toInteger())
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['isAuthenticated()'])
    def getDetailsByUser() {
        def auth = springSecurityService.currentUser
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Obtener detalles de deudor.", "Inicia Solicitud.", "usuario: ${params.userUuid}")
        if (!params.userUuid) {
            return respond(TypeError.missingParameter("usuario", logId, response))
        }
        def isOwner = auth?.uuid == params.userUuid
        def isPrivileged = auth?.authorities*.authority?.intersect(["ROLE_FINANCE", "ROLE_ADMIN"])
        if (!isOwner && !isPrivileged) {
            return respond(TypeError.noPermissions(logId, response))
        }
        if (params.userUuid.size() != 32) {
            return respond(TypeError.incorrectFormat("usuario", "UUID de 32 caracteres", logId, response))
        }
        def serviceResponse = saleService.getDetailsByUser(params.userUuid, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['ROLE_FINANCE'])
    def paySingleSale() {
        def saleUuid = params.saleUuid
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Pago de venta.", "Inicia Solicitud.", "sale: ${saleUuid}")
        if (!saleUuid) {
            return respond(TypeError.missingParameter("venta", logId, response))
        }
        if (saleUuid.size() != 32) {
            return respond(TypeError.incorrectFormat("venta", "UUID de 32 caracteres", logId, response))
        }
        def serviceResponse = saleService.paySingleSale(saleUuid, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['ROLE_FINANCE'])
    def paySingleDish() {
        def data = request.JSON
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Pago de platillo.", "Inicia Solicitud.", "data: ${data}")
        if (!data.saleUuid) {
            return respond(TypeError.missingParameter("venta", logId, response))
        }
        if (data.saleUuid.size() != 32) {
            return respond(TypeError.incorrectFormat("venta", "UUID de 32 caracteres", logId, response))
        }
        if (!data.orderItemUuid) {
            return respond(TypeError.missingParameter("platillo", logId, response))
        }
        if (data.orderItemUuid.size() != 32) {
            return respond(TypeError.incorrectFormat("platillo", "UUID de 32 caracteres", logId, response))
        }
        def serviceResponse = saleService.paySingleDish(data, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['ROLE_FINANCE'])
    def payMultipleSalesForUser() {
        def salesList = []
        def sales = request.JSON.sales
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Pago de multiples ventas.", "Inicia Solicitud.", "ventas: ${sales}")
        if (!sales) {
            return respond(TypeError.missingParameter("ventas", logId, response))
        }
        if (!sales.toString().isMultipleUuid()) {
            return respond(TypeError.incorrectFormat("venta", "una cadena de valores hexadecimales separados por comas", logId, response))
        }
        if (!sales.split(",").every { it.isUuid() }) {
            return respond(TypeError.incorrectFormat("venta", "un UUID de 32 caracteres", logId, response))
        }
        salesList = sales.split(",")
        def serviceResponse = saleService.payMultipleSalesForUser(salesList as ArrayList, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    @Secured(['ROLE_FINANCE'])
    def payAllSalesForUser() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Pago de todas las ventas.", "Inicia Solicitud.", "usuario: ${params.userUuid}")
        if (!params.userUuid) {
            return respond(TypeError.missingParameter("usuario", logId, response))
        }
        if (params.userUuid.size() != 32) {
            return respond(TypeError.incorrectFormat("usuario", "UUID de 32 caracteres", logId, response))
        }
        def serviceResponse = saleService.payAllSalesForUser(params.userUuid, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def getOneSaleInfo() {  
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Obtener informacion de venta.", "Inicia Solicitud.", "venta: ${params.saleUuid}")
        if (!params.saleUuid) {
            return respond(TypeError.missingParameter("venta", logId, response))
        }
        if (params.saleUuid.size() != 32) {
            return respond(TypeError.incorrectFormat("venta", "UUID de 32 caracteres", logId, response))
        }
        def serviceResponse = saleService.getOneSaleInfo(params.saleUuid, logId)  
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def getUserSalesByDateRange() {
        def auth = springSecurityService.currentUser
        def data = request.JSON
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Obtener compras en un rango de fechas.", "Inicia Solicitud.", "data: ${data}")
        if (!auth) {
            return respond(TypeError.missingParameter("usuario", logId, response))
        }
        if (!data.startDate) {
            return respond(TypeError.missingParameter("Fecha de inicio", logId, response))
        }
        if (!data.startDate.isLong() || data.startDate.size() != 13) {
            return respond(TypeError.incorrectFormat("Fecha de inicio", "valor numérico (milisegundos)", logId, response))
        }
        if (!data.endDate) {
            return respond(TypeError.missingParameter("Fecha de fin", logId, response))
        }
        if (!data.endDate.isLong() || data.endDate.size() != 13) {
            return respond(TypeError.incorrectFormat("Fecha de fin", "valor numérico (milisegundos)", logId, response))
        }
        if (new Date(data.endDate as long).before(new Date(data.startDate as long))) {
            return respond(TypeError.invalidData("fechas inicio/fin", logId, response))
        }

        def serviceResponse = saleService.getUserSalesByDateRange(data, auth, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def getSalesByUser() {
        def auth = springSecurityService.currentUser
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Obtener compras de un usuario.", "Inicia Solicitud.", "type: ${params.typeSale}")
        if (!auth) {
            return respond(TypeError.missingParameter("usuario", logId, response))
        }
        if (!params.typeSale) {
            return respond(TypeError.missingParameter("tipo de venta", logId, response))
        } 
        if (!(params.typeSale in ["Pending", "Paid", "all"])) {
            return respond(TypeError.incorrectFormat("tipo de venta", "[Pending, Paid, all]", logId, response))
        }
        def serviceResponse = saleService.getSalesByUser(auth, params.typeSale, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }

    def getUserSpendingChart() {
        def auth = springSecurityService.currentUser
        def data = request.JSON
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Obtener gastos de usuario.", "Inicia Solicitud.", "data: ${data}")
        
        if (!auth) {
            return respond(TypeError.missingParameter("usuario", logId, response))
        }
        if (!data.startDate) {
            return respond(TypeError.missingParameter("Fecha de inicio", logId, response))
        }
        if (!data.startDate.isLong() || data.startDate.size() != 13) {
            return respond(TypeError.incorrectFormat("Fecha de inicio", "valor numérico (milisegundos)", logId, response))
        }
        if (!data.endDate) {
            return respond(TypeError.missingParameter("Fecha de fin", logId, response))
        }
        if (!data.endDate.isLong() || data.endDate.size() != 13) {
            return respond(TypeError.incorrectFormat("Fecha de fin", "valor numérico (milisegundos)", logId, response))
        }
        if (new Date(data.endDate as long).before(new Date(data.startDate as long))) {
            return respond(TypeError.invalidData("fechas inicio/fin", logId, response))
        }

        def serviceResponse = saleService.getUserSpendingChart(data, auth, logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }
    
    @Secured(['ROLE_FINANCE','ROLE_ADMIN'])
    def getConsumeChart() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Obtener datos de consumo por tipo de menu.", "Inicia Solicitud.")
        def serviceResponse = saleService.getConsumeChart(logId)
        return respond(serviceResponse.data, status: serviceResponse.status)
    }
}
