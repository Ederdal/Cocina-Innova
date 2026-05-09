package com.ordenaris.finance

import grails.gorm.transactions.Transactional
import com.ordenaris.security.User
import com.ordenaris.order.*
import com.ordenaris.finance.Sale
import com.ordenaris.restaurant.MenuType
import com.ordenaris.Settings
import com.ordenaris.Constants
import org.hibernate.FetchMode
import java.text.SimpleDateFormat
import com.ordenaris.Log
import com.ordenaris.TypeError
import org.hibernate.transform.AliasToEntityMapResultTransformer

@Transactional
class SaleService {
    def mapUser(User user) {
        [
            uuid: user.uuid,
            username: user.username,
            name: user.names,
            lastname: user.lastNames,
            status: user.enabled
        ]
    }

    def mapOrder(Sale sale) {
        def orderItems = OrderItem.findAllByCustomerOrder(sale.customerOrder)
        [
            saleUuid: sale.uuid,
            orderUuid: sale.customerOrder.uuid,
            amount: sale.total,
            orderStatus: sale.customerOrder.status,
            dateCreated: sale.dateCreated.getTime(),
            daysPending: calculateDaysSince(sale.dateCreated),
            status: sale.status,
            items: orderItems.collect { item ->
                [
                    dishName: item.dish?.name ?: "Plato desconocido",
                    quantity: item.quantity,
                    unitPrice: item.unitPrice,
                    subtotal: item.quantity * item.unitPrice
                ]
            }
        ]
    }

    def calculateDaysSince(Date date) {
        if (!date) return 0
        return ((new Date().time - date.time) / (1000 * 60 * 60 * 24)).intValue()
    }

    def debtors = { query ->
        projections {
            property("uuid", "uuid")
            property("email", "email")
            property("username", "username")
            property("enabled", "enabled")
        }
        customerOrders {
            sale {
                property("total", "total")
                property("status", "status")
                eq("status", "Pending")
            }
        }
        if(query) {
            or {
                like("email", "%${query}%")
                like("username", "%${query}%")
            }
        }
        resultTransformer(AliasToEntityMapResultTransformer.INSTANCE)
    }

    def income = { query, column = null, start = null, end = null, status = null ->
        projections {
            property("uuid", "uuid")
            property("username", "username")
            property("names", "names")
            property("lastNames", "lastNames")
        }
        customerOrders {
            sale {
                property("dateCreated", "orderDate")
                property("lastUpdated", "paymentDate")
                property("total", "total")
                property("status", "status")
                order("lastUpdated", "desc")
                if (status) {
                    eq("status", status)
                }
                if (start && end) {
                    between(column, start, end)
                }
            }
        }
        if (query) {
            or {
                like("username", "%${query}%")
                like("names", "%${query}%")
                like("lastNames", "%${query}%")
            }
        }
        resultTransformer(AliasToEntityMapResultTransformer.INSTANCE)
    }

    def mapDebtors(data, hash) {
        data.each { row ->
            def uuid = row.uuid
            def total = row.total as BigDecimal
            
            if (!hash.containsKey(uuid)) {
                hash[uuid] = [
                    uuid: uuid,
                    email: row.email,
                    username: row.username,
                    status: row.enabled ?: false,
                    pendingOrdersCount: 0,
                    totalPendingAmount: 0.0
                ]
            }
            
            def userData = hash[uuid]
            userData.pendingOrdersCount += 1
            userData.totalPendingAmount += total
        }
    }

    def listDebtUsers(logId, query) {
        try {
            Log.logger(Log.INFO, logId, "Listado de deuda de usuarios.", "Llegada al servicio.", "query: ${query}")
            def usersData = User.createCriteria().list(debtors.curry(query))
            def debtsInfo = User.createCriteria().list(debtors.curry(""))
            def debtorsByUser = [:]
            def debtSummary = [:]
            
            mapDebtors(usersData, debtorsByUser)
            mapDebtors(debtsInfo, debtSummary)
            
            def debtors = debtorsByUser.values().sort { -it.totalPendingAmount }
            def totalDebtors = debtSummary.values()
            
            def summary = [
                totalDebtors: totalDebtors.size(),
                totalDebtAmount: totalDebtors.sum { it.totalPendingAmount } ?: 0.0
            ]
            
            Log.logger(Log.INFO, logId, "Listado de deuda de usuarios.", "Deudores obtenidos correctamente.", "query: ${query}", "deudores: ${debtors}")
            return [data: [success: true, data: [debtors: debtors, summary: summary]], status: 200]
            
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Listado de deuda de usuarios.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(100).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def getSalesPaid(logId, query, column, start, end, page, size) {
        try {
            Log.logger(Log.INFO, logId, "Listado de ingresos.", "Llegada al servicio.", "query: ${query}")
            def offset = page * size
            def incomeDefaultDaysValue = 14
            try {
                incomeDefaultDaysValue = Settings.findByIdentifier(Constants.INCOME_DEFAULT_DAYS)?.data?.toInteger() ?: 14
            } catch (NumberFormatException e) {
                Log.logger(Log.WARN, logId, "Listado de ingresos.", "Valor no numérico en setting INCOME_DEFAULT_DAYS, usando default", "default: 14")
            }
            def incomeDefaultDays = incomeDefaultDaysValue
            def startDate = start ? new Date(start as Long) : new Date() - incomeDefaultDays
            def endDate = end ? new Date(end as Long) : new Date()
            def usersData = User.createCriteria().list(max: size, offset: offset, income.curry(query, column, startDate, endDate, Constants.SALE_STATUS_PAID))
            def totalData = User.createCriteria().list(income.curry("", column, startDate, endDate))
            def mapIncomeRow = { row ->
                [
                    uuid: row.uuid,
                    status: row.status,
                    username: row.username,
                    name: "${row.names ?: ''} ${row.lastNames ?: ''}".trim(),
                    amount: row.total ?: 0.0,
                    orderDate: row.orderDate?.getTime(),
                    paymentDate: row.paymentDate?.getTime()
                ]
            }
            def dataIncome = usersData.collect(mapIncomeRow)
            def total = totalData.collect(mapIncomeRow)
            def totalSales = total.sum { it.amount } ?: 0.0
            def debtPayments = total.findAll { it.status == Constants.SALE_STATUS_PAID }
            def debtCollected = debtPayments.sum { it.amount } ?: 0.0
            def orderDebts   = total.findAll { it.status == Constants.SALE_STATUS_PENDING }
            def remainingDebt = orderDebts.sum { it.amount } ?: 0.0
            
            def summary = [
                paidOrders: debtPayments.size(),
                totalSales: totalSales,
                debtCollected: debtCollected,
                ordersPending: orderDebts.size(),
                remainingDebt: remainingDebt
            ]
            Log.logger( Log.INFO, logId, "Listado de ingresos.", "Ingresos obtenidos correctamente.", "query: ${query}", "total: ${totalSales}" )
            return [data: [success: true, data: [dataIncome: dataIncome, summary: summary]], status: 200]
        } catch(e) {
            Log.logger(Log.ERROR, logId, "Listado de ingresos.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(100).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def getDetailsByUser(userUuid, logId) {
        try {
            Log.logger(Log.INFO, logId, "Obtener detalles de deudor.", "Llegada al servicio.", "usuario: ${userUuid}")
            def user = User.findByUuid(userUuid)
            if (!user) {
                Log.logger(Log.WARN, logId, "Obtener detalles de deudor.", "No se encontro al usuario solicitado.", "usuario: ${userUuid}")
                return TypeError.informationNotFound(logId)
            }
            def pendingSales = Sale.createCriteria().list {
                eq("status", "Pending")
                customerOrder {
                    eq("user.id", user.id)
                }
            }
            def ordersData = pendingSales.collect { sale -> mapOrder(sale) }

            def debtorDetails = [
                user: mapUser(user),
                pendingOrders: ordersData,
                summary: [
                    totalPendingOrders: ordersData.size(),
                    totalPendingAmount: ordersData.sum { it.amount } ?: 0,
                    oldestOrderDate: ordersData ? ordersData.min { it.dateCreated }?.dateCreated : null
                ]
            ]

            Log.logger(Log.INFO, logId, "Obtener detalles de deudor.", "Información obtenida de manera exitosa.", "usuario: ${userUuid}", "debtorDetails: ${debtorDetails}")
            return [
                data: [success: true, data: debtorDetails, message: "Detalles del deudor obtenidos exitosamente"],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Obtener detalles de deudor.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def paySingleSale(saleUuid, logId) {
        try {
            Log.logger(Log.INFO, logId, "Pago de venta.", "Llegada al servicio.", "sale: ${saleUuid}")
            def sale = Sale.findByUuid(saleUuid)
            if (!sale) {
                Log.logger(Log.WARN, logId, "Pago de venta.", "No se encontro la venta solicitada.", "sale: ${saleUuid}")
                return TypeError.informationNotFound(logId)
            }
            if (sale.status != 'Pending') {
                Log.logger(Log.WARN, logId, "Pago de venta.", "La venta ya ha sido pagada.", "sale: ${saleUuid}")
                return TypeError.existingRegister(logId)
            }

            sale.status = 'Paid'
            sale.lastUpdated = new Date()
            sale.save(flush: true)

            def orderItems = OrderItem.createCriteria().list {
                eq("customerOrder", sale.customerOrder)
                eq("status", true)
            }

            orderItems.each { item ->
                item.payed = true
                item.save(flush: true)
            }

            Log.logger(Log.INFO, logId, "Pago de venta.", "Venta pagada exitosamente.", "sale: ${saleUuid}")
            return [
                data: [
                    success: true,
                    message: "Orden pagada exitosamente",
                    data: mapOrder(sale) + [paidDate: new Date()]
                ],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Pago de venta.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def payMultipleSalesForUser(salesList, logId) {
        try {
            Log.logger(Log.INFO, logId, "Pago de multiples ventas.", "Llega al servicio.", "ventas: ${salesList}")
            def sales = []
            
            for (saleUuid in salesList) {
                def sale = Sale.findByUuid(saleUuid)
                if (!sale) {
                    Log.logger(Log.WARN, logId, "Pago de multiples ventas.", "No existe la venta buscada.", "ventas: ${salesList}")
                    return TypeError.informationNotFound(logId)
                }
                sales << sale
            }

            if (!sales) {
                Log.logger(Log.WARN, logId, "Pago de multiples ventas.", "No se encontraron ventas para los UUIDs proporcionados.", "ventas: ${salesList}")
                return TypeError.informationNotFound(logId)
            }

            def idx = sales.findIndexOf { it.status == "Paid" }
            if (idx >= 0) {
                Log.logger(Log.WARN, logId, "Pago de multiples ventas.", "Una de las ventas ya ha sido pagada.", "uuid: ${sales[idx].uuid}", "ventas: ${salesList}")
                return TypeError.existingRegister(logId)
            }

            sales.each { sale ->
                sale.status = 'Paid'
                sale.lastUpdated = new Date()
                sale.save(flush: true, failOnError: true)

                def orderItems = OrderItem.createCriteria().list {
                    eq("customerOrder", sale.customerOrder)
                    eq("status", true)
                }

                orderItems.each { item ->
                    item.payed = true
                    item.save(flush: true, failOnError: true)
                }
            }

            Log.logger(Log.INFO, logId, "Pago de multiples ventas.", "Ventas pagadas correctamente.", "ventas: ${salesList}")
            return [data: [success: true, data: [ventas: sales.uuid]],status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Pago de multiples ventas.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def paySingleDish(data, logId) {
        try {
            Log.logger(Log.INFO, logId, "Pago de platillo.", "Llegada al servicio.", "data: ${data}")
            def sale = Sale.findByUuid(data.saleUuid)
            if (!sale) {
                Log.logger(Log.WARN, logId, "Pago de platillo.", "No se encontro la venta solicitada.", "data: ${data}")
                return TypeError.informationNotFound(logId)
            }
            if (sale.status != 'Pending') {
                Log.logger(Log.WARN, logId, "Pago de platillo.", "La venta ya ha sido pagada.", "data: ${data}")
                return TypeError.existingRegister(logId)
            }
            def orderItem = OrderItem.createCriteria().get {
                eq("customerOrder.id", sale.customerOrder.id)
                eq("status", true)
                eq("uuid", data.orderItemUuid)
            }

            if (!orderItem) {
                return TypeError.informationNotFound(logId)
            }

            if (orderItem.payed) {
                Log.logger(Log.WARN, logId, "Pago de platillo.", "El platillo ya ha sido pagado.", "data: ${data}")
                return TypeError.existingRegister(logId)
            }

            orderItem.payed = true
            orderItem.save(flush: true)

            def ordersLeftToPay = OrderItem.createCriteria().list {
                eq("customerOrder.id", sale.customerOrder.id)
                eq("status", true)
                eq("payed", false)
            }

            if (ordersLeftToPay.isEmpty()) {
                sale.status = 'Paid'
                sale.save(flush: true)
            }

            Log.logger(Log.INFO, logId, "Pago de platillo.", "Platillo pagado exitosamente.", "data: ${data}")
            return [
                data: [
                    success: true,
                    message: "Platillo pagado exitosamente",
                    data: [[
                        dishName: orderItem.dish?.name ?: "Plato desconocido",
                        quantity: orderItem.quantity,
                        unitPrice: orderItem.unitPrice,
                        subtotal: orderItem.quantity * orderItem.unitPrice
                    ]]
                ],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Pago de platillo.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def payAllSalesForUser(userUuid, logId) {
        try {
            Log.logger(Log.INFO, logId, "Pago de todas las ventas.", "Llegada al servicio.", "usuario: ${userUuid}")
            def user = User.findByUuid(userUuid)
            if (!user) {
                Log.logger(Log.WARN, logId, "Pago de todas las ventas.", "No se encontro al usuario solicitado.", "usuario: ${userUuid}")
                return TypeError.informationNotFound(logId)
            }

            def pendingSales = Sale.createCriteria().list {
                eq("status", "Pending")
                customerOrder {
                    eq("user.id", user.id)
                }
            }

            if (!pendingSales) {
                Log.logger(Log.WARN, logId, "Pago de todas las ventas.", "No hay ventas pendientes para este usuario.", "usuario: ${userUuid}")
                return TypeError.informationNotFound(logId)
            }

            def totalAmount = pendingSales.sum { it.total }
            def orderCount = pendingSales.size()

            pendingSales.each { sale ->
                sale.status = 'Paid'
                sale.lastUpdated = new Date()
                sale.save(flush: true)

                def orderItems = OrderItem.createCriteria().list {
                    eq("customerOrder", sale.customerOrder)
                    eq("status", true)
                }

                orderItems.each { item ->
                    item.payed = true
                    item.save(flush: true)
                }
            }

            Log.logger(Log.INFO, logId, "Pago de todas las ventas.", "Ventas pagadas exitosamente", "usuario: ${userUuid}")
            return [
                data: [
                    success: true,
                    message: "Todas las órdenes han sido pagadas exitosamente",
                    data: [
                        user: mapUser(user),
                        paidOrdersCount: orderCount,
                        totalAmountPaid: totalAmount,
                        paidDate: new Date()
                    ]
                ],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Pago de todas las ventas.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def createAutoSale(customerOrderUuid, logId) {
        try {
            Log.logger(Log.INFO, logId, "Creación de ventas.", "Llegada al servicio.", "orden: ${customerOrderUuid}")
            def order = CustomerOrder.findByUuid(customerOrderUuid)
            if (!order) {
                Log.logger(Log.WARN, logId, "Creación de ventas.", "No se encontro la orden solicitada.", "orden: ${customerOrderUuid}")
                return TypeError.informationNotFound(logId)
            }
            def items = OrderItem.findAllByCustomerOrder(order)
            def total = items.sum { it.unitPrice * it.quantity } ?: 0.0
            def newSale = new Sale([
                customerOrder: order,
                total: total
            ]).save(flush: true, failOnError: true)
            
            Log.logger(Log.INFO, logId, "Creación de ventas.", "Venta creada de forma exitosa.", "orden: ${customerOrderUuid}")
            return [
                data: [success: true, message: "Venta creada correctamente", data: newSale.uuid],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Creación de ventas.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def getOneSaleInfo(uuid, logId) {
        try {
            Log.logger(Log.INFO, logId, "Obtener información de venta.", "Llegada al servicio.", "venta: ${uuid}")
            def sale = Sale.findByUuid(uuid)
            
            if (!sale) {
                Log.logger(Log.INFO, logId, "Obtener información de venta.", "No se encontro la venta solicitada.", "venta: ${uuid}")
                return TypeError.informationNotFound(logId)
            }
                        
            def response = mapOrder(sale)
            Log.logger(Log.INFO, logId, "Obtener información de venta.", "Información obtenida exitosamente.", "venta: ${uuid}", "info: ${response}")
            return [
                data: [success: true, message: "Venta obtenida de manera correcta", data: response],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Obtener información de venta.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def getUserSalesByDateRange(data, auth, logId) {
        try {
            Log.logger(Log.INFO, logId, "Obtener compras en un rango de fechas.", "Llegada al servicio.", "data: ${data}")
            def list = Sale.createCriteria().list {
                customerOrder {
                    eq("user", auth)
                }
                between("dateCreated", new Date(data.startDate as long), new Date(data.endDate as long))
                order("dateCreated", "desc")
            }.collect { sale -> mapOrder(sale) }
            Log.logger(Log.INFO, logId, "Obtener compras en un rango de fechas.", "compras obtenidas exitosamente.", "data: ${data}")
            return [
                data: [success: true, message: "Ventas del rango especifico obtenidas exitosamente", data: list],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Obtener compras en un rango de fechas.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def getSalesByUser(auth, typeSale, logId) {
        try {
            Log.logger(Log.INFO, logId, "Obtener compras de un usuario.", "Llegada al servicio.", "type: ${typeSale}")
            def listOfSales = Sale.createCriteria().list {
                customerOrder {
                    eq("user", auth)
                }
                if (typeSale != "all") {
                    eq("status", typeSale)
                }
                order("dateCreated", "desc")
            }.collect { sale -> mapOrder(sale) }

            Log.logger(Log.INFO, logId, "Obtener compras de un usuario.", "Ventas obtenidas exitosamente.", "type: ${typeSale}", "ventas: ${listOfSales}")
            return [
                data: [success: true, message: "Compras del usuario obtenidas correctamente", data: listOfSales],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Obtener compras de un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }

    def getUserSpendingChart(data, auth, logId) {
        try {
            Log.logger(Log.INFO, logId, "Obtener gastos de usuario.", "Llegada al servicio.", "data: ${data}")
            def sales = Sale.createCriteria().list {
                customerOrder {
                    eq("user", auth)
                }
                between("dateCreated", new Date(data.startDate as long), new Date(data.endDate as long))
                order("dateCreated", "asc")
            }

            def dailyData = [:]
            def totalSpent = 0
            
            sales.each { sale ->
                def dateKey = new SimpleDateFormat("yyyy-MM-dd").format(sale.dateCreated)
                if (!dailyData[dateKey]) {
                    dailyData[dateKey] = 0
                }
                dailyData[dateKey] += sale.total
                totalSpent += sale.total
            }

            def dailyList = dailyData.collect { date, total ->
                [
                    date: date,
                    total: total
                ]
            }.sort { it.date }

            def transactionCount = sales.size()
            def averagePerTransaction = transactionCount > 0 ? (totalSpent / transactionCount) : 0
            def daysWithPurchases = dailyData.size()
            def averagePerDay = daysWithPurchases > 0 ? (totalSpent / daysWithPurchases) : 0

            Log.logger(Log.INFO, logId, "Obtener gastos de usuario.", "Gastos obtenidos exitosamente.", "data: ${data}")
            return [
                data: [
                    success: true,
                    message: "Gastos obtenidos correctamente",
                    data: [
                        daily: dailyList,
                        summary: [
                            totalSpent: totalSpent,
                            transactionCount: transactionCount,
                            averagePerTransaction: averagePerTransaction,
                            averagePerDay: averagePerDay,
                            daysWithPurchases: daysWithPurchases
                        ]
                    ]
                ],
                status: 200
            ]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Obtener gastos de usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }
    }
    
    def getConsumeChart(logId) {
        try {
            Log.logger(Log.INFO, logId, "Obtener datos de consumo por tipo de menu.", "Llegada al servicio.")
            def consumeChartDaysValue = 14
            try {
                consumeChartDaysValue = Settings.findByIdentifier(Constants.CONSUME_CHART_DAYS)?.data?.toInteger() ?: 14
            } catch (NumberFormatException e) {
                Log.logger(Log.WARN, logId, "Obtener datos de consumo por tipo de menu.", "Valor no numérico en setting CONSUME_CHART_DAYS, usando default", "default: 14")
            }
            def consumeChartDays = consumeChartDaysValue
            def start = new Date() - consumeChartDays
            def end = new Date()
            def sales = Sale.createCriteria().list {
                between("dateCreated", start, end)
                projections {
                    property("dateCreated", "date")
                }
                customerOrder {
                    orderItems {
                        dish {
                            menuType {
                                property("name", "menuType")
                            }
                        }
                    }
                }
                resultTransformer(AliasToEntityMapResultTransformer.INSTANCE)
            }

            def grouped = [:]

            sales.each { obj ->
                def (date, menuType) = [obj.date.format('yyyy-MM-dd'), obj.menuType]

                if (!grouped.containsKey(date)) {
                    grouped[date] = []
                }
                grouped[date] << menuType
            }

            def typeMenus = MenuType.findAllByStatus(1).collect{ it.name }

            def response = grouped.collect { date, menuType ->
                def result = [:]
                typeMenus.each{ _menu ->
                    result."$_menu" = menuType.count {it == _menu} 
                } 
                [
                    date: date,
                    menus: result 
                ]
            }

            Log.logger(Log.INFO, logId, "Obtener datos de consumo por tipo de menu.", "Data obtenida correctamente.")
            return [data: [success: true, data: response],status: 200]
        } catch (e) {
            Log.logger(Log.ERROR, logId, "Obtener datos de consumo por tipo de menu.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.message}", "stacktrace: ${e.stackTrace.take(10).join('\n')}")
            return TypeError.internalError(logId)
        }     
    }
}
