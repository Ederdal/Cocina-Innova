package com.ordenaris.restaurant
import com.ordenaris.Log


class MenuValidationUtils {
    static String validatePaginateTypesParams(def params, logId, grailsApplication) {
        if (!params.page) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "Falta el parámetro 'page'", "params: ${params}")
            return "La página no puede ir vacía"
        }
        if (!StringUtils.onlyNumbers(params.page?.toString())) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "El parámetro 'page' no contiene solo números", "page: ${params.page}")
            return "La página debe contener solo números"
        }
        if (!params.orderColumn) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "Falta el parámetro 'orderColumn'", "params: ${params}")
            return "El orderColumn no puede ir vacío"
        }
        if (!(params.orderColumn?.toString()?.trim() in RestaurantConstants.ALLOWED_SORT_COLUMNS)) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "orderColumn inválido", "orderColumn: ${params.orderColumn}")
            return "El orderColumn solo puede ser: " + RestaurantConstants.ALLOWED_SORT_COLUMNS.join(', ')
        }
        if (!params.order) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "Falta el parámetro 'order'", "params: ${params}")
            return "El order no puede ir vacío"
        }
        def orderDir = params.order?.toString()?.trim()
        if (!RestaurantConstants.ALLOWED_ORDER.contains(orderDir)) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "order inválido", "order: ${orderDir}")
            return "El order solo puede ser: " + RestaurantConstants.ALLOWED_ORDER.join(', ')
        }
        if (!params.max) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "Falta el parámetro 'max'", "params: ${params}")
            return "El max no puede ir vacio"
        }
        if (!StringUtils.onlyNumbers(params.max?.toString())) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "El parámetro 'max' no contiene solo números", "max: ${params.max}")
            return "El max debe contener solo números"
        }
        def allowedPageSizes = RestaurantConstants.getAllowedPageSizes(grailsApplication)
        if (!(params.max.toInteger() in allowedPageSizes)) {
            Log.logger(Log.WARN, logId, "Paginación tipos de menú", "max inválido", "max: ${params.max}")
            return "El max puede ser solo: " + allowedPageSizes.join(', ')
        }
        return null
    }
}
