
package com.ordenaris.order
import grails.gorm.transactions.Transactional
import grails.gorm.DetachedCriteria
import com.ordenaris.Log
import com.ordenaris.TypeError
import com.ordenaris.Constants
import com.ordenaris.Settings
import com.ordenaris.Conf
import com.ordenaris.security.User
import com.ordenaris.restaurant.Dish
import com.ordenaris.restaurant.EntityStatus
import com.ordenaris.order.CustomerOrder
import com.ordenaris.order.OrderItem
import com.ordenaris.finance.SaleService
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import java.time.LocalDate
import java.sql.Time
import java.time.ZoneId

@Transactional
class OrderModuleService {
    def saleService

    private int getChefSettingAsInt(String key, int defaultValue) {
        def value = Settings.findByIdentifier(key)?.data ?: Conf.getSettingsConf()[key]
        if (value == null) {
            return defaultValue
        }
        try {
            return value.toString().toInteger()
        } catch (Exception ignored) {
            return defaultValue
        }
    }

    private boolean isDishAvailableForToday(Dish dish) {
        if (!dish || dish.status != EntityStatus.ACTIVE) {
            return false
        }
        if (!dish.availableDate) {
            return true
        }

        def zoneId = ZoneId.of(Conf.getAppTimezone())
        def today = LocalDate.now(zoneId)
        def availableDay = dish.availableDate.toInstant().atZone(zoneId).toLocalDate()
        return availableDay.isEqual(today)
    }
    def mapOrder = { CustomerOrder order ->
        def orderResult = [
            uuid: order.uuid,
            status: order.status,
            user: order.user?.username,
            dateCreated: order.dateCreated?.getTime(),
            totalItems: order.orderItems?.size() ?: 0,
            dishes: order.orderItems.collect { item ->
                [
                    uuid: item.uuid,
                    quantityDish: item.quantity,
                    unitPrice: item.unitPrice / 100,
                    paid: item.payed,
                    dish: [
                        uuid: item.dish?.uuid,
                        name: item.dish?.name
                    ]
                ]
            }
        ]
        if(order.orderTime) orderResult.orderTime = order.orderTime.format("HH:mm")
        if(order.completedTime) orderResult.completedTime = order.completedTime.format("HH:mm")
        if(order.commentUser) orderResult.commentUser = order.commentUser
        if(order.commentChef) orderResult.commentChef = order.commentChef
        return orderResult
    }

    def orderCriteria = { params, query, userList, sort = null, orderMode = null ->
        if (params.start && params.end) {
            between("dateCreated", new Date(params.start as long), new Date(params.end as long))
        }
        if (params.status) {
            eq("status", params.status)
        }
        if (params.user && userList) {
            user {
                inList("id", userList)
            }
        }
        if (query) {
            or {
                ilike("commentUser", "%${query}%")
                ilike("commentChef", "%${query}%")
                
                inList("id", new DetachedCriteria(OrderItem).build {
                    projections { 
                        property("customerOrder.id")
                    }
                    dish {
                        ilike("name", "%${query}%")
                    }
                })
            }
        }
        if (sort && orderMode) {
            order(sort, orderMode)
        }
    }

    def listOrderByChef( chefId, logId) {
        try {
            def preparingVisibleLimit = getChefSettingAsInt(Constants.CHEF_PREPARING_VISIBLE_LIMIT, 2)
            def queueVisibleLimit = getChefSettingAsInt(Constants.CHEF_QUEUE_VISIBLE_LIMIT, 5)
            def preparingActiveLimit = getChefSettingAsInt(Constants.CHEF_PREPARING_ACTIVE_LIMIT, 2)
            def finishedVisibleLimit = getChefSettingAsInt(Constants.CHEF_FINISHED_VISIBLE_LIMIT, 100)
            def cancelledVisibleLimit = getChefSettingAsInt(Constants.CHEF_CANCELLED_VISIBLE_LIMIT, 100)

            def zoneId = ZoneId.of(Conf.getAppTimezone())
            def today = LocalDate.now(zoneId)
            def startOfDay = Date.from(today.atStartOfDay(zoneId).toInstant())
            def endOfDay = Date.from(today.plusDays(1).atStartOfDay(zoneId).toInstant())

            def chefUser = User.get(chefId)

            def preparingOrders = CustomerOrder.createCriteria().list {
                eq("status", "Preparing")
                eq("chef", chefUser)
                ge("dateCreated", startOfDay)
                lt("dateCreated", endOfDay)
                order("orderTime", "asc")
                maxResults(preparingVisibleLimit)
            }
            def preparingCount = CustomerOrder.createCriteria().count {
                eq("status", "Preparing")
                eq("chef", chefUser)
                ge("dateCreated", startOfDay)
                lt("dateCreated", endOfDay)
            }
            def finishedTodayOrders = CustomerOrder.createCriteria().list {
                eq("status", "Finished")
                eq("chef", chefUser)
                ge("dateCreated", startOfDay)
                lt("dateCreated", endOfDay)
                order("dateCreated", "desc")
                maxResults(finishedVisibleLimit)
            }
            def queueOrders = CustomerOrder.createCriteria().list {
                eq("status", "Queue")
                ge("dateCreated", startOfDay)
                lt("dateCreated", endOfDay)
                order("orderTime", "asc")
                maxResults(queueVisibleLimit)
            }
            def cancelledOrders = CustomerOrder.createCriteria().list {
                eq("status", "Cancelled")
                ge("dateCreated", startOfDay)
                lt("dateCreated", endOfDay)
                order("dateCreated", "desc")
                maxResults(cancelledVisibleLimit)
            }

            return [
                data: [
                    success: true,
                    queueOrders: queueOrders.collect { mapOrder(it) },
                    cancelledOrders: cancelledOrders.collect { mapOrder(it) },
                    preparingOrders: preparingOrders.collect { mapOrder(it) },
                    finishedTodayOrders: finishedTodayOrders.collect { mapOrder(it) },
                    finishedTodayCount: finishedTodayOrders.size(),
                    cancelledTodayCount: cancelledOrders.size(),
                    ordersPreparing: preparingCount,
                    canAcceptMore: preparingCount < preparingActiveLimit
                ],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Listar ordenes del chef.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)

        }
    }

    def listOrders(params, logId) {
        try {
            Log.logger(Log.INFO, logId, "Consultar las ordenes.", "Llega al servicio.", "params: $params")
            def size = params.max ? params.max as Integer : 10
            def offset = params.offset ? params.offset as Integer : 0
            def sort = params.sort ?: "dateCreated"
            def orderMode = params.order ?: "desc"
            def query = params.query ?: null
            def userList = params.list('users')
            def listCustomerOrders = CustomerOrder.createCriteria().list(max: size, offset: offset, orderCriteria.curry(params, query, userList, sort, orderMode))
            .collect { order ->
                mapOrder(order)
            }
            def totalCustomerOrders = CustomerOrder.createCriteria().count(orderCriteria.curry(params, query, userList))  
            Log.logger(Log.INFO, logId, "Consultar las ordenes.", "Fin de la solicitud, ordenes listadas.", "params: $params")
            return [data: [success: true, message: 'Ordenes listadas.', data: listCustomerOrders,total: totalCustomerOrders],status: 200]
        }
        catch(e){
            Log.logger(Log.ERROR, logId, "Consultar las ordenes.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
 
    def listOrdersByUser(data, userId, logId) {
        try {
            Log.logger(Log.INFO, logId, "Consultar ordenes usuario.", "Llega al servicio.", "data: $data")
            def user = User.get(userId)
            if (!user) {
                Log.logger(Log.ERROR, logId, "Consultar ordenes usuario.", "Usuario no encontrado.", "data: $data")
                return TypeError.missingParameter("usuario", logId)
            }
            def size = data.max ? data.max as Integer : 10
            def offset = data.offset ? data.offset as Integer : 0
            def sort = data.sort ?: "dateCreated"
            def orderMode = data.order ?: "desc"
            def query = data.query ?: null
 
            data.user = true
            def userList = [userId]
 
            def listCustomerOrders = CustomerOrder.createCriteria().list(max: size, offset: offset, orderCriteria.curry(data, query, userList, sort, orderMode))
            .collect { order ->
                mapOrder(order)
            }
           
            def totalCustomerOrders = CustomerOrder.createCriteria().count(orderCriteria.curry(data, query, userList))
 
            Log.logger(Log.INFO, logId, "Consultar ordenes usuario.", "Fin de la solicitud, ordenes listadas.", "data: $data")
            return [data: [success: true, message: 'Ordenes listadas', data: listCustomerOrders, total: totalCustomerOrders],status: 200]
        }
        catch (e) {
            Log.logger(Log.ERROR, logId, "Consultar ordenes usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
 
    def newOrder(data, auth, orderTime, commentUser, logId) {
        try {
            Log.logger(Log.INFO, logId, "Crear nueva orden.", "Llega al servicio.", "data: $data")
            def localNow = LocalTime.now(ZoneId.of(Conf.getAppTimezone()))
            def user = User.get(auth.id)
            if (!user) {
                Log.logger(Log.ERROR, logId, "Crear nueva orden.", "No se encontro al usuario.", "data: $data")
                return TypeError.missingParameter("usuario", logId)
            }
            if(!user.enabled ){
                Log.logger(Log.ERROR, logId, "Crear nueva orden.", "El usuario se encuentra desactivado y no puede ordenar nada.", "data: $data")
                return TypeError.resourceNotAvailable("que tu cuenta esta desactivada", logId)
            }
            if(orderTime.isBefore(localNow.plusHours(1)) && !localNow.isAfter(orderTime)) {
                Log.logger(Log.ERROR, logId, "Crear nueva orden.", "La orden debe programarse con al menos 1 hora de anticipacion.", "horario orden: $orderTime")
                return TypeError.invalidData("Horario de pedido", logId)          
            }
            if(commentUser == "") commentUser = null            
            def dishesData = []
            for (order in data) {
                def dish = Dish.findByUuid(order.dishUuid)
                if (!dish) {
                    Log.logger(Log.ERROR, logId, "Crear nueva orden.", "No se encuentra el platillo de la orden.", "data: $data")
                    return TypeError.missingParameter("platillo", logId)
                }
                if (!isDishAvailableForToday(dish)) {
                    Log.logger(Log.ERROR, logId, "Crear nueva orden.", "El platillo no esta disponible para hoy.", "dish: ${dish.uuid}")
                    return TypeError.resourceNotAvailable("platillo no disponible para hoy", logId)
                }
                if(dish.availableDishes != -1){
                    if (dish.availableDishes == 0) {
                        Log.logger(Log.ERROR, logId, "Crear nueva orden.", "No hay platillos en la cocina para crear la orden.", "data: $data")
                        return TypeError.resourceNotAvailable("no hay platillos disponibles", logId)
                    }
                    if( order.quantityDish > dish.availableDishes){
                        Log.logger(Log.ERROR, logId, "Crear nueva orden.", "Hay menos platillos en existencia de los que necesita la orden.", "data: $data")
                        return TypeError.resourceNotAvailable("insuficiencia de platillos, restantes: ${dish.availableDishes}", logId)
                    }
                }              
                dishesData << [dish: dish, quantity: order.quantityDish]
            }
            def customerOrder = new CustomerOrder([user:auth.id, orderTime:Time.valueOf(orderTime), commentUser: commentUser]).save(flush: true, failOnError: true)
            Log.logger(Log.INFO, logId, "Crear nueva orden.", "Orden creada.", "data: $data")
            for(item in dishesData){
                def orderItem = new OrderItem([
                    unitPrice: item.dish.cost,
                    dish: item.dish.id,
                    quantity: item.quantity,
                    customerOrder:customerOrder.id
                    ]).save(flush: true, failOnError: true)
                    if(item.dish.availableDishes != -1){
                        item.dish.availableDishes -= item.quantity
                        item.dish.save(flush: true, failOnError:true)
                    }
            }
            customerOrder.refresh()
            Log.logger(Log.INFO, logId, "Crear nueva orden.", "Se le asignaron los platillos a la orden.", "orden: $customerOrder")
            Log.logger(Log.INFO, logId, "Crear nueva orden.", "Fin de la solicitud.", "orden: $customerOrder")
            return [data: [success: true, message: 'Orden creada', order: mapOrder(customerOrder)],status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Crear nueva orden.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
   
    def addDishOrder(pathParams, requestBody, auth, logId){
        try{
            def user = User.get(auth.id)
            Log.logger(Log.INFO, logId, "Añadiendo nuevo platillo.", "Llega al servicio.", "json: $requestBody")
            if (!user) {
                Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "Usuario no encontrado.", "json: $requestBody")
                return TypeError.missingParameter("usuario", logId)
            }
            def order = CustomerOrder.findByUuid(pathParams.uuidOrder)
            if (!order) {
                Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "Orden no encontrada.", "json: $requestBody")
                return TypeError.missingParameter("orden", logId)
            }
            if (order.status == "Finished") {
                Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "Esta orden ya esta finalizada.", "estatus orden: ${order.status}")
                return TypeError.existingRegister(logId)
            }
            if (order.status == "Cancelled") {
                Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "Esta orden ya esta cancelada.", "estatus orden: ${order.status}")
                return TypeError.existingRegister(logId)
            }
            def existingItems = OrderItem.findAllByCustomerOrder(order)
            def existingDishUuids = existingItems.dish.uuid
            def searchDish = requestBody.uuidDish
            def dish = Dish.findByUuid(requestBody.uuidDish)
            if (!dish){
                Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "No existe ese platillo.", "json: $requestBody")
                return TypeError.informationNotFound(logId)
            }
            if (!isDishAvailableForToday(dish)) {
                Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "El platillo no esta disponible para hoy.", "json: $requestBody")
                return TypeError.resourceNotAvailable("platillo no disponible para hoy", logId)
            }
            def now = Time.valueOf(LocalTime.now(ZoneId.of(Conf.getAppTimezone())))
            def thirtyMinutesBefore = new Time(order.orderTime.time - (30 * 60 * 1000))
            if(now >= thirtyMinutesBefore){
                Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "No se puede agregar platillos 30 min despues de su horario solicitado.", "json: $requestBody")
                return TypeError.resourceNotAvailable("Se excedio el tiempo de modificacion", logId)
            }
            if(searchDish in existingDishUuids){
                for(item in existingItems){
                    if(item.dish.uuid == dish.uuid ){      
                        Log.logger(Log.INFO, logId, "Añadiendo nuevo platillo.", "Hay coincidencias en los platillos.", "Platillo: $item")
                        def newQuantityDish = item.quantity + requestBody.quantityDish
                        if(newQuantityDish > 5){
                            Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "Se excede el maximo de 5 por carrito.", "json: $requestBody")
                            return TypeError.excessRegister(logId)
                        }
                        if(dish.availableDishes != -1 && dish.availableDishes < requestBody.quantityDish){
                            Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "No hay suficientes platillos para añadir al carrito.", "json: $requestBody")
                            return TypeError.resourceNotAvailable("Los platillos no logran abastecer la orden", logId)
                        }
                        item.quantity = newQuantityDish
                        item.save(flush: true, failOnError: true)
                        if (dish.availableDishes != -1) {
                            dish.availableDishes -= requestBody.quantityDish
                            dish.save(flush: true, failOnError: true)
                        }
                        Log.logger(Log.INFO, logId, "Añadiendo nuevo platillo.", "Se agrego la nueva cantidad.", "Platillo: $item")
                        return [data: [success: true, message: "Se agrego la nueva cantidad."], status: 200]
                    }
                }
            }else{
                if(dish.availableDishes != -1 && dish.availableDishes < requestBody.quantityDish){
                    Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "No hay suficientes platillos.", "json: $requestBody")
                    return TypeError.resourceNotAvailable("Los platillos no logran abastecer la orden", logId)
                }
                def newOrderItem = new OrderItem([
                    unitPrice: dish.cost,
                    dish: dish.id,
                    quantity: requestBody.quantityDish,
                    customerOrder:order.id
                ]).save(flush: true, failOnError: true)
                if(dish.availableDishes != -1){
                    dish.availableDishes -= requestBody.quantityDish
                    dish.save(flush: true, failOnError: true)
                }
                Log.logger(Log.INFO, logId, "Añadiendo nuevo platillo.", "Se ha agregado el platillo a la orden.", "Platillo: $dish")
            }
            Log.logger(Log.INFO, logId, "Añadiendo nuevo platillo.", "Fin de la solicitud.", "Platillo: $dish")
            return [data: [success: true, message: 'Orden editada', order: mapOrder(order)],status: 200]
        }
        catch(e){
            Log.logger(Log.ERROR, logId, "Añadiendo nuevo platillo.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
 
    def editOrder(pathParams, requestBody, logId) {
        try {
            Log.logger(Log.INFO, logId, "Editar orden.", "Llega el servicio.", "json: $requestBody")
            def order = CustomerOrder.findByUuid(pathParams.uuidOrder)
            if (!order) {
                Log.logger(Log.ERROR, logId, "Editar orden.", "Orden no encontrada.", "json: $requestBody")
                return TypeError.missingParameter("orden", logId)
            }
            if (order.status == "Finished") {
                Log.logger(Log.ERROR, logId, "Editar orden.", "Esta orden ya esta finalizada.", "estatus orden: ${order.status}")
                return TypeError.excessRegister(logId)
            }
            if (order.status == "Cancelled") {
                Log.logger(Log.ERROR, logId, "Editar orden.", "Esta orden ya esta cancelada.", "estatus orden: ${order.status}")
                return TypeError.informationNotFound(logId)
            }
            def orderItem = OrderItem.findByUuidAndCustomerOrder(pathParams.uuidItem, order)
            if (!orderItem) {
                Log.logger(Log.ERROR, logId, "Editar orden.", "Platillo de la orden no encontrado.", "json: $requestBody")
                return TypeError.informationNotFound(logId)
            }
            def dish = Dish.findByUuid(requestBody.uuidDish)
            if (!dish) {
                Log.logger(Log.ERROR, logId, "Editar orden.", "Platillo no encontrado.", "json: $requestBody")
                return TypeError.informationNotFound(logId)
            }
            if (!isDishAvailableForToday(dish)) {
                Log.logger(Log.ERROR, logId, "Editar orden.", "El platillo no esta disponible para hoy.", "json: $requestBody")
                return TypeError.resourceNotAvailable("platillo no disponible para hoy", logId)
            }
            def oldDish = orderItem.dish
            def oldQuantity = orderItem.quantity
            orderItem.dish = dish
            orderItem.unitPrice = dish.cost
            orderItem.quantity = requestBody.quantityDish
            orderItem.save(flush: true, failOnError: true)
            if (oldDish.availableDishes != -1) {
                oldDish.availableDishes += oldQuantity
                oldDish.save(flush: true, failOnError: true)
            }
            if (dish.availableDishes != -1) {
                dish.availableDishes -= requestBody.quantityDish
                dish.save(flush: true, failOnError: true)
            }
            Log.logger(Log.INFO, logId, "Editar orden.", "Se ha actualizado tu orden.", "platillo: $orderItem")
            return [data: [success: true, message: "Se ha actualizado tu orden.", order: mapOrder(order)], status: 200]            
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Editar orden.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }      
    def editOrderStatus(data, completedTime, commentChef, logId) {
        if (!data.auth?.id) {
            Log.logger(Log.ERROR, logId, "Editar el estatus.", "No se recibió el usuario autenticado.", "data: $data")
            return TypeError.missingParameter("usuario autenticado", logId)
        }
        try {
            Log.logger(Log.INFO, logId, "Editar el estatus.", "Llega al servicio.", "data: $data")
            def order = CustomerOrder.findByUuid(data.uuidOrder)
            if (!order) return TypeError.missingParameter("Orden", logId)

            def chefUser = data.auth ? User.get(data.auth.id) : null

            if (data.status == "Preparing" && order.status != "Preparing") {
                def preparingActiveLimit = getChefSettingAsInt(Constants.CHEF_PREPARING_ACTIVE_LIMIT, 2)
                def zoneId = ZoneId.of(Conf.getAppTimezone())
                def today = LocalDate.now(zoneId)
                def startOfDay = Date.from(today.atStartOfDay(zoneId).toInstant())
                def endOfDay = Date.from(today.plusDays(1).atStartOfDay(zoneId).toInstant())
                def ordersPreparing = CustomerOrder.createCriteria().count {
                    eq("status", "Preparing")
                    eq("chef", chefUser)
                    ge("dateCreated", startOfDay)
                    lt("dateCreated", endOfDay)
                }
                if (ordersPreparing >= preparingActiveLimit) {
                    String limitMessage = "El chef ya tiene ${preparingActiveLimit} ordenes en proceso. No se pueden tener mas.".toString()
                    Log.logger(Log.ERROR, logId, "Editar el estatus.", "El chef ya alcanzo el limite de ordenes en proceso.", "preparingActiveLimit: $preparingActiveLimit", "data: $data")
                    return [data: [success: false, message: limitMessage], status: 409]
                }
                order.chef = chefUser
                Log.logger(Log.INFO, logId, "Editar el estatus.", "Se asigno el chef a la orden.", "chefUser: ${chefUser?.username}", "order: ${order.uuid}")
            }
            if (order.status == "Queue" && data.status == "Finished") {
                Log.logger(Log.ERROR, logId, "Editar el estatus.", "No se puede saltar el paso de preparacion de la orden, necesita primero que este en preparacion para poder finalizarla.", "data: $data")
                return TypeError.preconditionRequired(logId)
            }
            if (order.status == "Finished") {
                Log.logger(Log.ERROR, logId, "Editar el estatus.", "No se puede editar una orden que ya ha sido finalizada.", "data: $data")
                return TypeError.orderAlreadyFinalized(logId)
            }
            if (order.status == "Cancelled") {
                Log.logger(Log.ERROR, logId, "Editar el estatus.", "No se puede editar una orden que ya ha sido cancelada.", "data: $data")
                return TypeError.orderAlreadyCancelled(logId)
            }
            if (order.status == "Preparing" && data.status == "Queue") {
                Log.logger(Log.ERROR, logId, "Editar el estatus.", "No se puede regresar a cola una orden que ya esta siendo preparada.", "data: $data")
                return TypeError.excessRegister(logId)
            }
            if (order.status == "Preparing" && data.status == "Cancelled") {
                if (!order.chef || !chefUser || order.chef.id != chefUser.id) {
                    Log.logger(Log.ERROR, logId, "Editar el estatus.", "Solo el chef que inició la preparación puede cancelar la orden.", "data: $data")
                    return TypeError.permissionCustom("Solo el chef que inició la preparación puede cancelar la orden.", logId)
                }
            }
            if (data.status == "Finished") {
                if (!order.chef || !chefUser || order.chef.id != chefUser.id) {
                    Log.logger(Log.ERROR, logId, "Editar el estatus.", "Solo el chef que inició la preparación puede finalizar la orden.", "data: $data")
                    return TypeError.permissionCustom("Solo el chef que inició la preparación puede finalizar la orden.", logId)
                }
            }
            if (data.status in ["Cancelled", "Preparing", "Queue", "Finished"]) {
                order.status = data.status
                if (data.status == "Finished") {
                    Log.logger(Log.INFO, logId, "Editar el estatus.", "Tiene un estatus de finalizado.", "data: $data")
                    def sale = saleService.createAutoSale(order.uuid, logId)
                    if (sale.status != 200) {
                        Log.logger(Log.ERROR, logId, "Editar el estatus.", "Error al crear la venta, no se puede finalizar.", "data: $data")
                        return [data: sale.data, status: sale.status]
                    }
                    order.commentChef = commentChef
                    order.completedTime = Time.valueOf(completedTime)
                    Log.logger(Log.INFO, logId, "Editar el estatus.", "Se ha finalizado su orden y se creo la venta.", "data: $data")
                }
                if (data.status == "Cancelled") {
                    Log.logger(Log.INFO, logId, "Editar el estatus.", "Tiene un estatus de cancelado.", "data: $data")
                    if (!commentChef) {
                        Log.logger(Log.ERROR, logId, "Editar el estatus.", "No se encontro el comentario del chef.", "data: $data")
                        return TypeError.missingParameter("comentario del chef", logId)
                    } else {
                        order.commentChef = commentChef
                        order.completedTime = null
                        Log.logger(Log.INFO, logId, "Editar el estatus.", "Se ha cancelado la orden con exito.", "data: $data")
                    }
                }
                order.save(flush: true, failOnError: true)
                Log.logger(Log.INFO, logId, "Editar el estatus.", "Se ha actualizado el estatus de la orden.", "data: $data")
                return [data: [success: true, message: 'Estado de la orden actualizado a ' + data.status, order: mapOrder(order)], status: 200]
            }
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Editar el estatus.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def rejectDish(String uuidOrder, String uuidItem, String reason, chef) {
        try {
            def order = CustomerOrder.findByUuid(uuidOrder)
            if (!order) return [data: [success: false, message: "Orden no encontrada"],status: 400]

            def orderItem = OrderItem.findByUuidAndCustomerOrder(uuidItem, order)
            if (!orderItem) {
                return [
                    data: [success: false, message: "Platillo no encontrado en la orden"],
                    status: 400
                ]
            }

            def chefUser = User.get(chef.id)
            if (!chefUser) {
                return [
                    data: [success: false, message: "Chef no encontrado"],
                    status: 400
                ]
            }

            def rejection = new DishRejection(
                reason: reason,
                order: order,
                orderItem: orderItem,
                chef: chefUser,
                approvalStatus: "Pending"
            )
            rejection.save(flush: true, failOnError: true)

            return [
                data: [
                    success: true,
                    message: "Platillo rechazado. Se ha notificado al usuario.",
                    rejection: [
                        uuid: rejection.uuid,
                        reason: rejection.reason,
                        approvalStatus: rejection.approvalStatus,
                        dish: [
                            uuid: orderItem.dish?.uuid,
                            name: orderItem.dish?.name
                        ]
                    ]
                ],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Rechazar platillo.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return [
                data: [success: false, message: "Error al rechazar platillo, Inténtalo más tarde."],
                status: 500
            ]
        }
    }

    def listRejections(auth) {
        try {
            def user = User.get(auth.id)
            if (!user) {
                return [
                    data: [success: false, message: "Usuario no encontrado"],
                    status: 400
                ]
            }

            def rejections = DishRejection.createCriteria().list {
                eq("approvalStatus", "Pending")
                order {
                    eq("user", user)
                }
                order('dateCreated', 'desc')
            }

            def formattedRejections = rejections.collect { rejection ->
                [
                    uuid: rejection.uuid,
                    reason: rejection.reason,
                    approvalStatus: rejection.approvalStatus,
                    dateCreated: rejection.dateCreated,
                    chef: [
                        username: rejection.chef?.username
                    ],
                    order: [
                        uuid: rejection.order?.uuid,
                        status: rejection.order?.status
                    ],
                    dish: [
                        uuid: rejection.orderItem?.dish?.uuid,
                        name: rejection.orderItem?.dish?.name,
                        quantity: rejection.orderItem?.quantity
                    ]
                ]
            }

            return [
                data: [success: true, message: "Ordenes rechazadas", rejections: formattedRejections],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Error general en orden.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return [
                data: [success: false, message: "Error al procesar la solicitud, Inténtalo más tarde."],
                status: 500
            ]
        }
    }

    def rejectionInfo(String rejectionUuid) {
        try {
            def rejection = DishRejection.findByUuid(rejectionUuid)
            if (!rejection) {
                return [
                    data: [success: false, message: "Rechazo no encontrado"],
                    status: 400
                ]
            }

            return [
                data: [
                    success: true, 
                    message: "Informacion del rechazo",
                    rejection: [
                        uuid: rejection.uuid,
                        reason: rejection.reason,
                        approvalStatus: rejection.approvalStatus,
                        dateCreated: rejection.dateCreated,
                        dateApproved: rejection.dateApproved,
                        chef: [
                            username: rejection.chef?.username
                        ],
                        order: [
                            uuid: rejection.order?.uuid,
                            status: rejection.order?.status
                        ],
                        dish: [
                            uuid: rejection.orderItem?.dish?.uuid,
                            name: rejection.orderItem?.dish?.name,
                            quantity: rejection.orderItem?.quantity,
                            unitPrice: rejection.orderItem?.unitPrice / 100
                        ]
                    ]
                ],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, null, "Consultar rechazo de platillo", "Error al obtener información de rechazo", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return [
                data: [success: false, message: "Error al consultar información del rechazo, Inténtalo más tarde."],
                status: 500
            ]
        }
    }

    def approveRejection(String rejectionUuid, auth) {
        try {
            def rejection = DishRejection.findByUuid(rejectionUuid)
            if (!rejection) {
                return [
                    data: [success: false, message: "Rechazo no encontrado"],
                    status: 400
                ]
            }

            def user = User.get(auth.id)
            if (rejection.order.user.id != user.id) {
                return [
                    data: [success: false, message: "No tiene permiso para aprobar este rechazo"],
                    status: 403
                ]
            }

            if (rejection.approvalStatus != "Pending") {
                return [
                    data: [success: false, message: "Este rechazo ya fue procesado"],
                    status: 400
                ]
            }

            rejection.approvalStatus = "Approved"
            rejection.dateApproved = new Date()
            rejection.save(flush: true, failOnError: true)

            def orderItem = rejection.orderItem
            def order = rejection.order
            order.removeFromOrderItems(orderItem)
            orderItem.delete(flush: true, failOnError: true)

            if (order.orderItems.size() == 0) {
                order.status = "Cancelled"
                order.save(flush: true, failOnError: true)
            }

            return [
                data: [
                    success: true,
                    message: "Rechazo aprobado. El platillo ha sido removido de la orden."
                ],
                status: 200
            ]
        } catch (e) {
            return [
                data: [success: false, message: "Error interno. Inténtalo más tarde."],
                status: 500
            ]
        }
    }

    def cancelRejection(String rejectionUuid, auth) {
        try {
            def rejection = DishRejection.findByUuid(rejectionUuid)
            if (!rejection) {
                return [
                    data: [success: false, message: "Rechazo no encontrado"],
                    status: 400
                ]
            }

            def user = User.get(auth.id)
            if (rejection.order.user.id != user.id) {
                return [
                    data: [success: false, message: "No tiene permiso para cancelar este rechazo"],
                    status: 403
                ]
            }

            if (rejection.approvalStatus != "Pending") {
                return [
                    data: [success: false, message: "Este rechazo ya fue procesado"],
                    status: 400
                ]
            }

            rejection.approvalStatus = "Cancelled"
            rejection.dateApproved = new Date()
            rejection.save(flush: true, failOnError: true)

            return [
                data: [
                    success: true,
                    message: "Rechazo cancelado. El platillo permanecerá en la orden."
                ],
                status: 200
            ]
        } catch (e) {
            return [
                data: [success: false, message: "Error interno. Inténtalo más tarde."],
                status: 500
            ]
        }
    }
}
