package com.ordenaris.restaurant
import grails.gorm.transactions.Transactional
import com.ordenaris.order.OrderItem
import java.util.UUID
import java.util.Calendar
import java.time.ZoneId
import java.time.LocalDate
import org.springframework.web.multipart.MultipartFile
import grails.util.Holders
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.ordenaris.TypeError
import com.ordenaris.Log
import com.ordenaris.Constants
import com.ordenaris.Conf




@Transactional
class DishService {
    ReviewService reviewService
    private Map getTodayRange() {
        def zoneId = ZoneId.of(Conf.getAppTimezone())
        def today = LocalDate.now(zoneId)
        def startOfDay = Date.from(today.atStartOfDay(zoneId).toInstant())
        def endOfDay = Date.from(today.plusDays(1).atStartOfDay(zoneId).toInstant())
        return [startOfDay: startOfDay, endOfDay: endOfDay]
    }

    private void synchronizeScheduledDishesStatuses(String logId) {
        def todayRange = getTodayRange()
        def activated = Dish.executeUpdate(
            "update Dish d set d.status = :active where d.availableDate is not null and d.availableDate >= :start and d.availableDate < :end and d.status <> :deleted and d.status <> :active",
            [
                active: EntityStatus.ACTIVE,
                deleted: EntityStatus.DELETED,
                start: todayRange.startOfDay,
                end: todayRange.endOfDay
            ]
        )

        def inactivated = Dish.executeUpdate(
            "update Dish d set d.status = :inactive where d.availableDate is not null and (d.availableDate < :start or d.availableDate >= :end) and d.status = :active",
            [
                inactive: EntityStatus.INACTIVE,
                active: EntityStatus.ACTIVE,
                start: todayRange.startOfDay,
                end: todayRange.endOfDay
            ]
        )

        if (activated || inactivated) {
            Log.logger(Log.INFO, logId, "Sincronizar platillos programados", "Estatus de platillos actualizado por fecha", "activated: ${activated}, inactivated: ${inactivated}")
        }
    }

    Map saveDishImageByUuid(String uuid, MultipartFile file) {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        try {
            def dish = Dish.findByUuid(uuid)
            if (!dish) {
                Log.logger(Log.WARN, logId, "Guardar imagen platillo", "Platillo no encontrado", "uuid: ${uuid}")
                return [data: [success: false, message: "Datos inválidos para platillo"], status: 404]
            }
            saveDishImage(dish, file)
            return [data: [success: true, message: "Imagen guardada exitosamente"], status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Guardar imagen platillo", "Error al guardar imagen", e.getMessage())
            return [data: [success: false, message:  "Ha ocurrido un error al guardar la imagen."], status: 400]
        }
    }

    Map reloadDishImageByUuid(String uuid, MultipartFile file) {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        try {
            def dish = Dish.findByUuid(uuid)
            if (!dish) {
                Log.logger(Log.WARN, logId, "Recargar imagen platillo", "Platillo no encontrado", "uuid: ${uuid}")
                return [data: [success: false, message: "Datos inválidos para platillo"], status: 404]
            }
            reloadDishImage(dish, file)
            return [data: [success: true, message: "Imagen actualizada"], status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Recargar imagen platillo", "Error al recargar imagen", e.getMessage())
            return [data: [success: false, message: "Ha ocurrido un error al recargar la imagen."], status: 400]
        }
    }

    void deleteDishImageByUuid(String uuid) {
        def dish = Dish.findByUuid(uuid)
        if (!dish) throw new IllegalArgumentException("Datos inválidos para platillo")
        deleteDishImage(dish)
    }

    File resolveDishImageByUuidPublic(String uuid) {
        def dish = Dish.findByUuid(uuid)
        if (!dish) return null
        return resolveDishImage(dish)
    }



    private Closure buildDishFilters(Map params) {
        def query = params.query ? params.query.toString().trim() : null
        def statusValue = params.status != null
            ? (params.status instanceof Integer ? params.status : params.status.toString().toInteger())
            : null
        return {
            if (statusValue != null) eq("status", statusValue)
            if (params.availableDishes != null) {
                if (params.availableDishes == -1) {
                    eq("availableDishes", params.availableDishes)
                } else if (params.availableDishes == 0) {
                    eq("availableDishes", params.availableDishes)
                } else if (params.availableDishes > 0) {
                    gt("availableDishes", 0)
                }
            }
            createAlias("menuType", "mt")
            if (params.menuUuid) {
                eq("mt.uuid", params.menuUuid)
            }
            eq("mt.status", EntityStatus.ACTIVE)
            ne("status", EntityStatus.DELETED)
            if (query) {
                or {
                    like("name", "%${query}%")
                    like("description", "%${query}%")
                }
            }
        }
    }

    private def buildPaginatedDishes(Map params) {
        int page = params.page instanceof Integer ? params.page : params.page?.toString()?.toInteger()
        int max = params.max instanceof Integer ? params.max : params.max?.toString()?.toInteger()
        def offset = page * max - max
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Dish.createCriteria().list {
            buildDishFilters(params).rehydrate(delegate, owner, this).call()
            firstResult(offset)
            maxResults(max)
            order(params.orderColumn as String, params.order as String)
        }.collect { dish ->
            def menuType = dish.menuType
            def outOfScheduleMessage = null
            if (menuType?.startTime && menuType?.endTime) {
                def now = new Date().format('HH:mm', TimeZone.getTimeZone(Conf.getAppTimezone()))
                if (now < menuType.startTime || now > menuType.endTime) {
                    outOfScheduleMessage = "Lo sentimos, este platillo está disponible en un horario de [${menuType.startTime} - ${menuType.endTime}]"
                }
            }
            def totalReviews = Review.countByDishAndStatus(dish, EntityStatus.ACTIVE)
            [
                uuid: dish.uuid,
                name: dish.name,
                description: dish.description,
                cost: dish.cost / 100,
                status: dish.status,
                availableDishes: dish.availableDishes,
                availableDate: dish.availableDate,
                imageUrl: dish.imageUrl,
                subMenu: mapMenuType(dish.menuType, []),
                outOfScheduleMessage: outOfScheduleMessage,
                comments: totalReviews
            ]
        }
    }

    private Dish validateEditDishStatusParams(status, uuid, String logId) {
        def validStatus = [EntityStatus.ACTIVE, EntityStatus.INACTIVE, EntityStatus.DELETED]
        if (!validStatus.contains(status)) {
            return TypeError.invalidData("status", logId)
        }
        def dish = Dish.findByUuid(uuid)
        if (!dish) {
            return TypeError.invalidData("platillo", logId)
        }
        if (dish.status == EntityStatus.DELETED) {
            return TypeError.invalidData("platillo eliminado", logId)
        }
        return dish
    }


    private Dish validateEditDishParams(def dishData, String logId) {
        def dish = Dish.findByUuid(dishData.uuid)
        if (!dish) {
            Log.logger(Log.WARN, logId, "Validación fallida", "El platillo no existe", "params: ${dishData}")
            return TypeError.invalidData("platillo", logId)
        }
        if (dish.status == EntityStatus.DELETED) {
            Log.logger(Log.WARN, logId, "Validación fallida", "El platillo está eliminado y no puede modificarse", "params: ${dishData}")
            return TypeError.invalidData("platillo eliminado", logId)
        }
        return dish
    }

    private def assignMenuTypeIfNeeded(Dish dish, def dishData, String logId) {
        if (dishData.menuType && dishData.menuType != dish.menuType?.uuid) {
            def newMenuType = MenuType.findByUuid(dishData.menuType)
            if (!newMenuType) {
                Log.logger(Log.WARN, logId, "Validación fallida", "El tipo de menú no existe", "params: ${dishData}")
                return [error: true, message: "El tipo de menú no existe"]
            }
            dish.menuType = newMenuType
        }
        return [error: false]
    }

    private def assignEditableDishFields(Dish dish, def dishData, grailsApplication) {
        if (dishData.name != null) dish.name = dishData.name ? dishData.name.trim() : null
        if (dishData.availableDate != null) dish.availableDate = dishData.availableDate
        if (dishData.description != null) dish.description = dishData.description ? dishData.description.trim() : null
        if (dishData.imageUrl != null) dish.imageUrl = dishData.imageUrl ? dishData.imageUrl.trim() : null
        if (dishData.cost != null) {
            def maxCost = grailsApplication.config.restaurant.dish.maxCost
            if ((dishData.cost / 100) > maxCost) {
                return [typeError: TypeError.invalidData("costo", null)]
            }
            dish.cost = dishData.cost
        }
        if (dishData.availableDishes != null) {
            def maxDishes = grailsApplication.config.restaurant.dish.maxAvailableDishes
            if (dishData.availableDishes > maxDishes) {
                return [typeError: TypeError.invalidData("platillos disponibles", null)]
            }
            dish.availableDishes = dishData.availableDishes
        }
    }

    private Object validateDishInfoParams(String uuid, String logId) {
        def dish = Dish.findByUuid(uuid)
        if (!dish) {
            return [data: [success: false, message: "El platillo no existe"], status: 404]
        }
        if (dish.status == EntityStatus.DELETED) {
            return [data: [success: false, message: "El platillo no existe"], status: 404]
        }
        return dish
    }

    private Map buildDishInfoResponse(Dish dish, requestedStatus) {
        def response = [
            uuid: dish.uuid,
            id: dish.id,
            name: dish.name,
            description: dish.description,
            cost: dish.cost / 100,
            status: dish.status,
            availableDishes: dish.availableDishes,
            availableDate: dish.availableDate,
            imageUrl: dish.imageUrl,
        ]
        if (requestedStatus != null && requestedStatus == EntityStatus.ACTIVE) {
            response.subMenu = mapMenuType(dish.menuType, [])
        }
        return response
    }


    private def validateIncrementParams(uuid, quantity, logId) {
        def maxDishes = grailsApplication?.config?.restaurant?.dish?.maxAvailableDishes ?: 50
        def error = validateQuantityMax(quantity, maxDishes, logId, uuid)
        if (error) return error
        error = validateQuantityMin(quantity, logId, uuid)
        if (error) return error
        def dish = Dish.findByUuid(uuid)
        error = validateDishExists(dish, logId, uuid, quantity)
        if (error) return error
        error = validateDishActive(dish, logId, uuid, quantity)
        if (error) return error
        error = validateDishUnlimited(dish, logId, uuid, quantity)
        if (error) return error
        return dish
    }

    private def validateQuantityMax(quantity, maxDishes, logId, uuid) {
        if (quantity > maxDishes) {
            Log.logger(Log.WARN, logId, "Validación fallida", "La cantidad no puede exceder los ${maxDishes}", "params: { uuid: ${uuid}, quantity: ${quantity} }")
            return TypeError.invalidData("La cantidad no puede exceder ${maxDishes}", logId)
        }
        return null
    }

    private def validateQuantityMin(quantity, logId, uuid) {
        if (quantity < 1) {
            Log.logger(Log.WARN, logId, "Validación fallida", "La cantidad debe ser mayor a 0", "params: { uuid: ${uuid}, quantity: ${quantity} }")
            return TypeError.invalidData("cantidad", logId)
        }
        return null
    }

    private def validateDishExists(dish, logId, uuid, quantity) {
        if (!dish) {
            Log.logger(Log.WARN, logId, "Validación fallida", "Platillo no encontrado", "params: { uuid: ${uuid}, quantity: ${quantity} }")
            return TypeError.informationNotFound(logId)
        }
        return null
    }

    private def validateDishActive(dish, logId, uuid, quantity) {
        if (dish.status != EntityStatus.ACTIVE) {
            Log.logger(Log.WARN, logId, "Validación fallida", "No se puede modificar un platillo inactivo o eliminado", "params: { uuid: ${uuid}, quantity: ${quantity} }")
            return TypeError.invalidData("platillo inactivo o eliminado", logId)
        }
        return null
    }

    private def validateDishUnlimited(dish, logId, uuid, quantity) {
        if (dish.availableDishes == -1) {
            Log.logger(Log.WARN, logId, "Validación fallida", "Este platillo tiene disponibilidad ilimitada, no necesita incremento", "params: { uuid: ${uuid}, quantity: ${quantity} }")
            return TypeError.invalidData("disponibilidad ilimitada", logId)
        }
        return null
    }


    private Integer updateAvailableDishes(Dish dish, Integer quantity) {
        def previousQuantity = dish.availableDishes
        dish.availableDishes += quantity
        dish.save(flush: true, failOnError: true)
        return previousQuantity
    }

    private def getMenuTypeOrRespond(def menuTypeUuid, String logId, def dishData) {
        def menuTypeObj = MenuType.findByUuid(menuTypeUuid)
        if (!menuTypeObj) {
            Log.logger(Log.WARN, logId, "Validación fallida", "Tipo de menu no encontrado", "params: ${dishData}")
            return [typeError: TypeError.invalidData("tipo de menú", logId)]
        }
        return menuTypeObj
    }

    private Dish createAndSaveDish(def dishData, MenuType menuTypeObj) {
        def dishStatus
        if (dishData.availableDate != null) {
            def todayRange = getTodayRange()
            if (dishData.availableDate >= todayRange.startOfDay && dishData.availableDate < todayRange.endOfDay) {
                dishStatus = EntityStatus.ACTIVE
            } else {
                dishStatus = EntityStatus.INACTIVE
            }
        } else {
            dishStatus = EntityStatus.ACTIVE
        }
        new Dish([
            name: dishData.name,
            menuType: menuTypeObj,
            availableDate: dishData.availableDate,
            cost: dishData.cost,
            description: dishData.description,
            availableDishes: dishData.availableDishes != null ? dishData.availableDishes : -1,
            imageUrl: dishData.imageUrl,
            status: dishStatus
        ]).save(flush: true, failOnError: true)
    }

    private List mapTopDishesChartData(orderItems) {
        orderItems.groupBy { it.dish }.collect { dish, items ->
            def totalQuantity = items.sum { it.quantity } ?: 0
            def totalRevenue = items.sum { (it.unitPrice ?: 0) * (it.quantity ?: 0) } ?: 0
            [
                uuid: dish?.uuid,
                name: dish?.name,
                quantity: totalQuantity,
                revenue: totalRevenue,
                orders: items.size()
            ]
        }.sort { -it.quantity }
    }

        private Map mapDish(dish) {
            [
                uuid: dish.uuid,
                id: dish.id,
                name: dish.name,
                description: dish.description,
                cost: dish.cost / 100,
                status: dish.status,
                availableDishes: dish.availableDishes,
                availableDate: dish.availableDate,
                imageUrl: dish.imageUrl,
            ]
        }

        private Map mapMenuTypeWithDishes(type) {
            def dishes = Dish.findAllByStatusNotEqualsAndMenuType(EntityStatus.DELETED, type).collect { mapDish(it) }
            if (dishes.size() > 0) {
                return [uuid: type.uuid, name: type.name, dishes: dishes]
            }
            def submenu = MenuType.findAllByStatusNotEqualsAndParentType(EntityStatus.DELETED, type).collect { subtype ->
                def subdishes = Dish.findAllByStatusNotEqualsAndMenuType(EntityStatus.DELETED, subtype).collect { mapDish(it) }
                if (subdishes.size() > 0) {
                    return [uuid: subtype.uuid, name: subtype.name, dishes: subdishes]
                }
                return null
            }.findAll { it != null }
            if (submenu.size() > 0) {
                return [uuid: type.uuid, name: type.name, submenu: submenu]
            }
            return null
        }
    
    def grailsApplication

    def listActiveDishes() {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Listar platillos activos.", "Inicia solicitud.")
        try {
            synchronizeScheduledDishesStatuses(logId)
            
            def now = new Date().format('HH:mm')
            def isWithinSchedule = { menuType ->
                def start = menuType.startTime ?: menuType.parentType?.startTime
                def end = menuType.endTime ?: menuType.parentType?.endTime
                if (!start || !end) return true 
                return (now >= start && now <= end)
            }
            def roots = MenuType.findAllByStatusAndParentTypeIsNull(EntityStatus.ACTIVE)
            def data = roots.findAll { isWithinSchedule(it) }.collect { type ->
                def dishes = Dish.findAllByStatusAndMenuType(EntityStatus.ACTIVE, type).collect { d ->
                    [
                        uuid: d.uuid, id: d.id, name: d.name, description: d.description,
                        cost: d.cost / 100, status: d.status, availableDishes: d.availableDishes,
                        availableDate: d.availableDate, imageUrl: d.imageUrl,
                    ]
                }
                if (dishes) [uuid: type.uuid, name: type.name, startTime: type.startTime, endTime: type.endTime, dishes: dishes]
                else {
                    def submenu = MenuType.findAllByStatusAndParentType(EntityStatus.ACTIVE, type)
                        .findAll { isWithinSchedule(it) }
                        .collect { sub ->
                            def subdishes = Dish.findAllByStatusAndMenuType(EntityStatus.ACTIVE, sub).collect { d ->
                                [
                                    uuid: d.uuid, name: d.name, description: d.description,
                                    cost: d.cost / 100, status: d.status, availableDishes: d.availableDishes,
                                    availableDate: d.availableDate, imageUrl: d.imageUrl,
                                ]
                            }
                            def start = sub.startTime ?: sub.parentType?.startTime
                            def end = sub.endTime ?: sub.parentType?.endTime
                            subdishes ? [uuid: sub.uuid, name: sub.name, startTime: start, endTime: end, dishes: subdishes] : null
                        }.findAll { it != null }
                    submenu ? [uuid: type.uuid, name: type.name, startTime: type.startTime, endTime: type.endTime, submenu: submenu] : null
                }
            }.findAll { it != null }
            Log.logger(Log.INFO, logId, "Listar platillos activos.", "Platillos activos listados.", "now: ${now}, roots: ${roots?.size() ?: 0}, data: ${data?.size() ?: 0}")
            return [data: [success: true, data: data, message: "Platillos activos"], status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Listar platillos activos.", "Algo ha salido mal.", e.getMessage())
            return [data: [success: false, message: "Error interno del servidor."], status: 500]
        }
    }

    def listAllDishes() {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Listar todos los platillos.", "Inicia solicitud.")
        try {
            def roots = MenuType.findAllByStatusNotEqualsAndParentTypeIsNull(EntityStatus.DELETED)
            def data = roots.collect { type ->
                def dishes = Dish.findAllByStatusNotEqualsAndMenuType(EntityStatus.DELETED, type).collect { d ->
                    [
                        uuid: d.uuid, id: d.id, name: d.name, description: d.description,
                        cost: d.cost / 100, status: d.status, availableDishes: d.availableDishes,
                        availableDate: d.availableDate
                    ]
                }
                if (dishes) [uuid: type.uuid, name: type.name, dishes: dishes]
                else {
                    def submenu = MenuType.findAllByStatusNotEqualsAndParentType(EntityStatus.DELETED, type).collect { sub ->
                        def subdishes = Dish.findAllByStatusNotEqualsAndMenuType(EntityStatus.DELETED, sub).collect { d ->
                            [
                                uuid: d.uuid, name: d.name, description: d.description,
                                cost: d.cost / 100, status: d.status, availableDishes: d.availableDishes,
                                availableDate: d.availableDate
                            ]
                        }
                        subdishes ? [uuid: sub.uuid, name: sub.name, dishes: subdishes] : null
                    }.findAll { it != null }
                    submenu ? [uuid: type.uuid, name: type.name, submenu: submenu] : null
                }
            }.findAll { it != null }
            Log.logger(Log.INFO, logId, "Listar todos los platillos.", "Todos los platillos listados.", "roots: ${roots?.size() ?: 0}, data: ${data?.size() ?: 0}")
            return [data: [success: true, data: data, message: "Todos los platillos (no borrados)"], status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Listar todos los platillos.", "Algo ha salido mal.", e.getMessage())
            return [data: [success: false, message: "Error interno del servidor."], status: 500]
        }
    }
    
def getTopDishesChart(Integer days = null, Integer limit = null) {
    def logId = UUID.randomUUID().toString().replaceAll('-', '')
    Log.logger(Log.INFO, logId, "Top platillos más vendidos", "Inicia Solicitud", "params: { days: ${days}, limit: ${limit} }")
    try {
        def defaultDays = grailsApplication.config.restaurant.dish.topDishesChartDays
        def defaultLimit = grailsApplication.config.restaurant.dish.topDishesChartLimit
        if (days == null || days < 1) days = defaultDays
        if (limit == null || limit < 1) limit = defaultLimit
        def calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -days)
        def startDate = calendar.time
        def orderItems = OrderItem.createCriteria().list {
            between('dateCreated', startDate, new Date())
            eq('status', true)
        }
        def chartData = mapTopDishesChartData(orderItems)
        Log.logger(Log.INFO, logId, "Top platillos más vendidos", "Top platillos más vendidos obtenido", "result: ${chartData.take(limit)}")
        return [
            data: [
                success: true,
                data: [message: "Top ${limit} platillos en últimos ${days} días", 
                    chartData: chartData.take(limit)
                ]
            ],
            status: 200
        ]
    } catch (e) {
        Log.logger(Log.ERROR, logId, "Top platillos más vendidos", "Error en getTopDishesChart", e.getMessage(), "params: { days: ${days}, limit: ${limit} }")
        return [data: [success: false, message: "Ha ocurrido un error, intenta más tarde."], status: 500]
    }
}


def mapMenuType = { type, dishes ->
    if (!type) {
        return null
    }

    def dataMenu = [
        uuid: type.uuid,
        name: type.name
    ]
    if (dishes.size() > 0) {
        if (dishes[0].description) {
            dataMenu.dishes = dishes
            return dataMenu
        }
        dataMenu.submenu = dishes
    }
    return dataMenu
}

def newDish(dishData, MultipartFile imageFile = null) {
    def logId = UUID.randomUUID().toString().replaceAll('-', '')
    Log.logger(Log.INFO, logId, "Crear nuevo platillo", "Inicia Solicitud", "params: ${dishData}")
    try {
        dishData.name = dishData.name ? dishData.name.trim() : null
        def menuTypeObj = getMenuTypeOrRespond(dishData.menuType, logId, dishData)
        if (menuTypeObj instanceof Map && menuTypeObj.typeError) {
            return [
                data: [success: false, message: "No se puede crear porque el tipo de menú no existe"],
                status: 404
            ]
        }
        def vName = DishValidationUtils.validateDishName(dishData.name, grailsApplication)
        if (vName instanceof Map && vName.success == false) return [data: vName, status: 400]
        def vMenuType = DishValidationUtils.validateMenuType(dishData.menuType)
        if (vMenuType instanceof Map && vMenuType.success == false) return [data: vMenuType, status: 400]
        def vDesc = DishValidationUtils.validateDishDescription(dishData.description, grailsApplication)
        if (vDesc instanceof Map && vDesc.success == false) return [data: vDesc, status: 400]
        def vAvail = DishValidationUtils.validateAvailableDishes(dishData.availableDishes, grailsApplication)
        if (vAvail instanceof Map && vAvail.success == false) return [data: vAvail, status: 400]
        def vCost = DishValidationUtils.validateDishCost(dishData.cost)
        if (vCost instanceof Map && vCost.success == false) return [data: vCost, status: 400]
        def vCostMax = DishValidationUtils.convertAndValidateCost(dishData.cost, grailsApplication)
        if (vCostMax instanceof Map && vCostMax.success == false) return [data: vCostMax, status: 400]
        dishData.cost = vCostMax
        String sourceDishUuid = dishData.sourceDishUuid
        if (sourceDishUuid) {
            def sourceDish = Dish.findByUuid(sourceDishUuid)
            if (!sourceDish) {
                Log.logger(Log.WARN, logId, "Validación fallida", "Platillo origen para clonar no existe", "params: ${dishData}")
                return [data: [success: false, message: 'El platillo origen no existe'], status: 404]
            }
            if (sourceDish.status == EntityStatus.DELETED) {
                Log.logger(Log.WARN, logId, "Validación fallida", "Platillo origen para clonar está eliminado", "params: ${dishData}")
                return [data: [success: false, message: 'No se puede clonar un platillo eliminado'], status: 409]
            }
            if ((!dishData.imageUrl || !dishData.imageUrl.toString().trim()) && (!imageFile || imageFile.empty)) {
                dishData.imageUrl = sourceDish.imageUrl
            }

            def activeUpdated = Dish.executeUpdate(
                "update Dish d set d.status = :inactive where d.name = :name and d.menuType = :menuType and d.status = :active",
                [
                    inactive: EntityStatus.INACTIVE,
                    name: dishData.name,
                    menuType: menuTypeObj,
                    active: EntityStatus.ACTIVE
                ]
            )

            def previousStatus = sourceDish.status
            Dish.executeUpdate(
                "update Dish d set d.status = :inactive where d.uuid = :uuid and d.status <> :deleted",
                [inactive: EntityStatus.INACTIVE, uuid: sourceDishUuid, deleted: EntityStatus.DELETED]
            )
            sourceDish.refresh()
            Log.logger(
                Log.INFO,
                logId,
                "Clonar platillo",
                "Platillo origen deshabilitado para crear clon",
                "uuid: ${sourceDish.uuid}, previousStatus: ${previousStatus}, newStatus: ${sourceDish.status}, disabledByNameAndMenu: ${activeUpdated}"
            )
        }

        def newDish = createAndSaveDish(dishData, menuTypeObj)
        if (imageFile && !imageFile.empty) {
            try{
            saveDishImage(newDish, imageFile)
            }catch(e){
                Log.logger(Log.WARN, logId, "Crear nuevo platillo","Platillo creado pero la imagen no se pudo guardar", "error: ${e.getMessage()}" )
            }
        }
        Log.logger(Log.INFO, logId, "Resultado exitoso", "Nuevo platillo creado", "result: ${newDish.uuid}")
        return [
            data: [
                success: true,
                data:[
                    newDish: newDish.uuid, message: "Nuevo platillo creado"
                ]
            ],
            status: 200
        ]
    } catch (IllegalArgumentException e) {
        throw e
    } catch (e) {
        Log.logger(Log.ERROR, logId, "Error en newDish", e.getMessage(), "params: ${dishData}")
        return TypeError.internalError(logId)
    }
}

def incrementAvailableDishes(uuid, quantity) {
    def logId = UUID.randomUUID().toString().replaceAll('-', '')
    Log.logger(Log.INFO, logId, "Incrementar disponibilidad de platillo", "Inicia Solicitud", "params: { uuid: ${uuid}, quantity: ${quantity} }")
    try {
        def dish = validateIncrementParams(uuid, quantity, logId)
        Log.logger(Log.INFO, logId, "DEBUG", "Valor de dish tras validación", "dish: ${dish?.toString()} | tipo: ${dish?.getClass()?.getName()}")
        if (dish instanceof Map && dish.status) {
            def msg = dish.data?.message
            if (msg instanceof Map || msg instanceof List) {
                msg = "Error de validación"
            }
            if (msg?.contains("información solicitada")) {
                msg = "El platillo con uuid ${uuid} no existe"
            }
            if (msg?.toLowerCase()?.contains("ilimitada")) {
                msg = "No se pudo actualizar la disponibilidad del platillo porque ya es ilimitado"
            }
            return [data: [success: false, message: (msg ?: "Error de validación").toString()], status: dish.status]
        }
        if (dish instanceof Dish) {
            def previousQuantity = updateAvailableDishes(dish, quantity)
            Log.logger(Log.INFO, logId, "Resultado exitoso", "Disponibilidad de platillo incrementada", "result: {previous: ${previousQuantity}, new: ${dish.availableDishes}, increment: ${quantity}}")
            return [
                data: [
                    success: true,
                    message: "Platillos disponibles actualizados correctamente",
                    previousQuantity: previousQuantity,
                    newQuantity: dish.availableDishes,
                    increment: quantity
                ],
                status: 200
            ]
        }
    } catch (e) {
        Log.logger(Log.ERROR, logId, "Error en incrementAvailableDishes", e.getMessage(), "params: { uuid: ${uuid}, quantity: ${quantity} }")
        if (e instanceof TypeError) {
            return [data: [success: false, message: "Error interno del servidor."], status: 400]
        }
        return TypeError.internalError(logId)
    }
}

def dishInfo(uuid, requestedStatus = null) {
    def logId = UUID.randomUUID().toString().replaceAll('-', '')
    Log.logger(Log.INFO, logId, "Info de platillo", "Inicia Solicitud", "params: { uuid: ${uuid}, requestedStatus: ${requestedStatus} }")
    if (!uuid || uuid.toString().trim().size() != 32) {
        Log.logger(Log.WARN, logId, "Info platillo", "UUID inválido", "uuid: ${uuid}")
        return [data: [success: false, message: "Access is denied"], status: 403]
    }
    def dishOrError = validateDishInfoParams(uuid, logId)
    if (dishOrError instanceof Map && dishOrError.status) {
        return [
            data: dishOrError.data ?: [success: false, message: "Error desconocido"],
            status: dishOrError.status
        ]
    }
    def response = buildDishInfoResponse(dishOrError, requestedStatus)
    Log.logger(Log.INFO, logId, "Resultado exitoso", "Información de platillo obtenida", "result: ${response}")
    return [
        data: [
            success: true,
            data: [message: "Información del platillo.", chartData: response]
        ],
        status: 200
    ]
}

def editDish(dishData) {
    def logId = UUID.randomUUID().toString().replaceAll('-', '')
    Log.logger(Log.INFO, logId, "Editar platillo", "Inicia Solicitud", "params: ${dishData}")
    try {
        def dish = validateEditDishParams(dishData, logId)
        def menuTypeResult = assignMenuTypeIfNeeded(dish, dishData, logId)
        if (menuTypeResult && menuTypeResult.error) {
            return [
                data: [success: false, message: menuTypeResult.message ?: "El tipo de menú no existe"],
                status: 400
            ]
        }
        assignEditableDishFields(dish, dishData, grailsApplication)
        dish.save(flush: true, failOnError: true)
        Log.logger(Log.INFO, logId, "Resultado exitoso", "Platillo actualizado correctamente", "result: ${dish.uuid}")
        return [
            data: [success: true, message: "Platillo actualizado correctamente"],
            status: 200
        ]
    } catch (e) {
        Log.logger(Log.ERROR, logId, "Error en editDish", e.getMessage(), "params: ${dishData}")
        def msg = "Ha ocurrido un error, inténtalo más tarde."
        def statusCode = 500
        if (e.getMessage() == "El platillo no existe o está eliminado") {
            msg = e.getMessage()
            statusCode = 404
        }
        return [data: [success: false, message: msg], status: statusCode]
    }
}
def editDishStatus(status, uuid, Date availableDate = null) {
    def logId = UUID.randomUUID().toString().replaceAll('-', '')
    Log.logger(Log.INFO, logId, "Editar status de platillo", "Inicia Solicitud", "params: { status: ${status}, uuid: ${uuid}, availableDate: ${availableDate} }")
    def dish = Dish.findByUuid(uuid)
    if (!dish) {
        Log.logger(Log.WARN, logId, "Editar status platillo", "Platillo no encontrado", "uuid: ${uuid}")
        return [data: [success: false, message: "Platillo no encontrado"], status: 404]
    }
    if (dish.status == EntityStatus.DELETED) {
        def actionMsg = ""
        switch(status) {
            case EntityStatus.ACTIVE:
                actionMsg = "No se puede activar porque el platillo ya está eliminado"
                break
            case EntityStatus.INACTIVE:
                actionMsg = "No se puede desactivar porque el platillo ya está eliminado"
                break
            case EntityStatus.DELETED:
                actionMsg = "No se puede eliminar porque el platillo ya está eliminado"
                break
            default:
                actionMsg = "No se puede cambiar el estado porque el platillo ya está eliminado"
        }
        Log.logger(Log.WARN, logId, "Editar status platillo", actionMsg, "uuid: ${uuid}")
        return [data: [success: false, message: actionMsg], status: 409]
    }
    try {
        if (availableDate != null) {
            dish.availableDate = availableDate
        }

        if (status == EntityStatus.INACTIVE && availableDate == null) {
            dish.availableDate = null
        }

        def responseMessage = "Estado del platillo actualizado"
        if (status == EntityStatus.ACTIVE && availableDate != null) {
            def todayRange = getTodayRange()
            if (availableDate >= todayRange.startOfDay && availableDate < todayRange.endOfDay) {
                dish.status = EntityStatus.ACTIVE
                responseMessage = "Platillo activado para hoy"
            } else {
                dish.status = EntityStatus.INACTIVE
                responseMessage = "Platillo programado para la fecha indicada"
            }
        } else {
            dish.status = status
        }

        dish.save(flush: true, failOnError: true)
        Log.logger(Log.INFO, logId, "Resultado exitoso", "Estado del platillo actualizado", "result: ${dish.uuid}, status: ${dish.status}, availableDate: ${dish.availableDate}")
        return [
            data: [success: true, message: responseMessage],
            status: 200
        ]
    } catch (e) {
        Log.logger(Log.ERROR, logId, "Error en editDishStatus", e.getMessage(), "params: { status: ${status}, uuid: ${uuid} }")
        return TypeError.internalError(logId)
    }
}
    
def paginateDishes(Map params) {
    def logId = UUID.randomUUID().toString().replaceAll('-', '')
    Log.logger(Log.INFO, logId, "Paginar platillos", "Entrada al servicio", "params: ${params}")
    def errorMsg = DishValidationUtils.validatePaginateDishesParams(params, logId, grailsApplication)
    if (errorMsg) {
        return [data: [success: false, message: errorMsg], status: 400]
    }
    try {
        def paginatedDishes = buildPaginatedDishes(params)
        def total = Dish.createCriteria().count {
            buildDishFilters(params).rehydrate(delegate, owner, this).call()
        }
        Log.logger(Log.INFO, logId, "Paginar platillos", "Resultado de buildPaginatedDishes", "pageSize: ${paginatedDishes.size()}", "totalRecords: ${total}")

        def message = "Platillos paginados"
        if (paginatedDishes.isEmpty()) {
            message = "No encontramos resultados para tu búsqueda."
            Log.logger(Log.INFO, logId, "Paginar platillos", "Sin resultados para la búsqueda", "params: ${params}")
        }
        Log.logger(Log.INFO, logId, "Paginar platillos", "Respuesta enviada", "message: ${message}")
        return [
            data: [
                success: true,
                data: [
                    message: message,
                    chartData: paginatedDishes,
                    total: total
                ]
            ],
            status: 200
        ]
    } catch (e) {
        Log.logger(Log.ERROR, logId, "Paginar platillos", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "params: ${params}")
        return [data: [success: false, message: "Ha ocurrido un error, intenta más tarde."], status: 500]
    }
}

def getDishRankingByRating(int limit = 10) {
    try {
        def results = Review.createCriteria().list {
            createAlias("dish", "d")

            ne("d.status", EntityStatus.DELETED)

            projections {
                groupProperty("d.uuid")
                groupProperty("d.name")
                groupProperty("d.description")
                groupProperty("d.cost")
                avg("rating", "avgRating")
                count("id", "reviewCount")
            }

            order("avgRating", "desc")
            order("reviewCount", "desc")

            maxResults(limit)
        }

        def ranking = results.collect { row ->
            [
                uuid          : row[0],
                name          : row[1],
                description   : row[2],
                cost          : row[3] / 100,
                averageRating : (row[4] ?: 0).round(2),
                reviewCount   : row[5]?.toInteger() ?: 0
            ]
        }

        return [
            data: [
                success: true,
                data: [message: "Ranking de platillos por calificación",
                chartData: ranking,
                total: ranking.size(),
                ]
            ],
            status: 200
        ]
    } catch (e) {
        Log.logger(Log.ERROR, UUID.randomUUID().toString().replaceAll('-', ''), "Error en getDishRankingByRating", e.getMessage(), "params: { limit: ${limit} }")
        return [
            data: [success: false, message: "Ha ocurrido un error, intenta más tarde."],
            status: 500
        ]
    }
}
def getTopSellingDishes(int limit = 10) {
    try {
        def results = OrderItem.createCriteria().list {
            createAlias("dish", "d")

            eq("status", true)
            ne("d.status", EntityStatus.DELETED)

                projections {
                    groupProperty("d.uuid")
                    groupProperty("d.name")
                    groupProperty("d.description")
                    groupProperty("d.cost")
                    sum("quantity", "totalSold")
                }

            order("totalSold", "desc")
            maxResults(limit)
        }

        def topDishes = results.collect { row ->
            [
                uuid       : row[0],
                name       : row[1],
                description: row[2],
                cost       : row[3] / 100,
                totalSold  : row[4]?.toInteger() ?: 0
            ]
        }

    return [
        data: [
            success: true,
            data: [message:"Top platillos más vendidos", 
            chartData:  topDishes,
            total: topDishes.size(), ]
        ],
        status: 200
    ]
    } catch (e) {
        Log.logger(Log.ERROR, UUID.randomUUID().toString().replaceAll('-', ''), "Error en getTopSellingDishes", e.getMessage(), "params: { limit: ${limit} }")
        return [
            data: [success: false, message: "Ha ocurrido un error, intenta más tarde."],
            status: 500
        ]
    }
}

    private String resolveBasePath() {
        def configuredBasePath = Holders.config?.app?.upload?.basePath ?: Holders.config?.repository
        if (!configuredBasePath) {
            throw new IllegalArgumentException("Ruta de carga de imágenes no configurada")
        }
        return normalizeToAbsolutePath(configuredBasePath.toString())
    }

    private String normalizeToAbsolutePath(String pathStr) {
        if (!(pathStr =~ /^[a-zA-Z]:\\|^[a-zA-Z]:\//) && !pathStr.startsWith("/")) {
            String userHome = System.getProperty("user.home")
            pathStr = userHome + File.separator + pathStr
        }
        return pathStr
    }

    private Path resolveDishImagesDir() {
        def configuredUploadDir = Holders.config?.restaurant?.images?.'upload-dir'
        if (configuredUploadDir) {
            return Paths.get(normalizeToAbsolutePath(configuredUploadDir.toString()))
        }
        return Paths.get(resolveBasePath(), 'uploads', 'dishes')
    }

    private File resolveDefaultDishImage() {
        Path defaultInUploads = resolveDishImagesDir().resolve("default.jpg")
        if (Files.exists(defaultInUploads)) {
            return defaultInUploads.toFile()
        }
        return Paths.get(resolveBasePath(), 'dishes', 'default.jpg').toFile()
    }


    void saveDishImage(Dish dish, MultipartFile file) {
        def logId = UUID.randomUUID().toString().replaceAll('-', '')
        Log.logger(Log.INFO, logId, "Guardar imagen platillo", "Inicia guardado de imagen", "uuid: ${dish?.uuid}, originalFilename: ${file?.originalFilename}")
        try {
            validateFile(file)
            deleteDishImageFiles(dish)

            Path dishDir = resolveDishImagesDir()
            Files.createDirectories(dishDir)

            String extension = extractExtension(file.originalFilename)
            String filename = "dish_${dish.uuid}${extension}"

            Path targetPath = dishDir.resolve(filename)
            Log.logger(Log.INFO, logId, "Guardar imagen platillo", "Intentando guardar archivo", "path: ${targetPath}")
            file.transferTo(targetPath.toFile())

            String relativePath = "uploads/dishes/${filename}"
            dish.imageUrl = relativePath
            dish.lastImageModified = new Date()
            if (!dish.dateImageUploaded) {
                dish.dateImageUploaded = new Date()
            }
            dish.save(flush: true, failOnError: true)
            Log.logger(Log.INFO, logId, "Guardar imagen platillo", "Imagen guardada exitosamente", "imageUrl: ${dish.imageUrl}")
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Guardar imagen platillo", "Error al guardar imagen", e.getMessage())
            throw e
        }
    }
    
    void reloadDishImage(Dish dish, MultipartFile file) {
        if (!dish) {
            throw new IllegalArgumentException("Platillo requerido")
        }
        validateFile(file)
        saveDishImage(dish, file)
    }
    
    private void deleteDishImageFiles(Dish dish) {
        if (!dish) return
        
        Path baseImageDir = resolveDishImagesDir()
        if (!Files.exists(baseImageDir)) return
        
        def allowedExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp']
        
        try {
            Files.walk(baseImageDir)
                .filter { path -> Files.isRegularFile(path) }
                .forEach { path ->
                    String fileName = path.fileName.toString()
                    if (fileName.startsWith("dish_${dish.uuid}")) {
                        try {
                            Files.delete(path)
                        } catch (e) {
                            Log.logger(Log.WARN, UUID.randomUUID().toString().replaceAll('-', ''), "Eliminar imagen platillo", "No se pudo eliminar archivo", "path: ${path}, error: ${e.getMessage()}")
                        }
                    }
                }
        } catch (e) {
            Log.logger(Log.WARN, UUID.randomUUID().toString().replaceAll('-', ''), "Eliminar imagen platillo", "Error al recorrer directorio de imágenes", e.getMessage())
        }
    }

    File resolveDishImage(Dish dish) {
        if (!dish || !dish.imageUrl) {
            return resolveDefaultDishImage()
        }
        
        
        Path baseImageDir = resolveDishImagesDir()
        if (!Files.exists(baseImageDir)) {
            return resolveDefaultDishImage()
        }
        
        
        String fileName = dish.imageUrl.split('/').last()
        
        try {
            def found = Files.walk(baseImageDir)
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == fileName }
                .findFirst()
            if (found.isPresent()) {
                return found.get().toFile()
            }
        } catch (e) {
            Log.logger(Log.WARN, UUID.randomUUID().toString().replaceAll('-', ''), "Buscar imagen platillo", "Error al buscar imagen", e.getMessage())
        }
        
        return resolveDefaultDishImage()
    }

    File resolveDishImageByFileName(String fileName) {
        Path baseImageDir = resolveDishImagesDir()
        if (Files.exists(baseImageDir)) {
            try {
                def found = Files.walk(baseImageDir)
                    .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == fileName }
                    .findFirst()
                if (found.isPresent()) {
                    return found.get().toFile()
                }
            } catch (e) {
                Log.logger(Log.WARN, UUID.randomUUID().toString().replaceAll('-', ''), "Buscar imagen platillo por nombre", "Error al buscar imagen", e.getMessage())
            }
        }

        return resolveDefaultDishImage()
    }

    void deleteDishImage(Dish dish) {
        if (!dish) return

        deleteDishImageFiles(dish)
        dish.imageUrl = null
        dish.lastImageModified = new Date()
        dish.save(flush: true, failOnError: true)
    }


    private void validateFile(MultipartFile file) {
        if (!file || file.empty) {
            throw new IllegalArgumentException("El archivo de imagen es requerido")
        }
        if (!Constants.ALLOWED_TYPES.contains(file.contentType)) {
            throw new IllegalArgumentException("El archivo debe ser de tipo [image/jpeg, image/png, image/webp]")
        }
        if (file.size > Constants.MAX_SIZE) {
            throw new IllegalArgumentException("El archivo debe ser una imagen no mayor a 2MB")
        }
    }

    private String extractExtension(String filename) {
        return filename.substring(filename.lastIndexOf('.')).toLowerCase()
    }

    File resolveDishImageByUuid(String uuid) {
        Path baseImageDir = resolveDishImagesDir()
        if (!Files.exists(baseImageDir)) {
            return resolveDefaultDishImage()
        }

        def allowedExtensions = ['.jpg', '.jpeg', '.png', '.gif', '.webp']
        
        try {
            for (ext in allowedExtensions) {
                def found = Files.walk(baseImageDir)
                    .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == "dish_${uuid}${ext}" }
                    .findFirst()
                if (found.isPresent()) {
                    return found.get().toFile()
                }
            }
        } catch (e) {
            Log.logger(Log.WARN, UUID.randomUUID().toString().replaceAll('-', ''), "Buscar imagen platillo por UUID", "Error al buscar imagen", e.getMessage())
        }

        return resolveDefaultDishImage()
    }

}

