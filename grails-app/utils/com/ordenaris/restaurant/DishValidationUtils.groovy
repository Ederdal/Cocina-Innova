package com.ordenaris.restaurant
import com.ordenaris.TypeError
import com.ordenaris.Conf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class DishValidationUtils {
    private static boolean isEmptyOrTemplateDateValue(def availableDate) {
        if (availableDate == null) {
            return true
        }

        def rawValue = availableDate.toString().trim()
        if (!rawValue) {
            return true
        }

        def lowerValue = rawValue.toLowerCase()
        if (lowerValue == 'null' || lowerValue == 'undefined' || lowerValue == '-1') {
            return true
        }

        return rawValue.startsWith('{{') && rawValue.endsWith('}}')
    }

    static String validatePaginateDishesParams(def params, logId, grailsApplication) {
        if (!params.page) {
            return "La página no puede ir vacía"
        }
        if (!StringUtils.onlyNumbers(params.page?.toString())) {
            return "La página debe contener solo números"
        }
        if (!params.orderColumn) {
            return "El orderColumn no puede ir vacío"
        }
        if (!(params.orderColumn?.toString()?.trim() in com.ordenaris.restaurant.RestaurantConstants.ALLOWED_SORT_COLUMNS)) {
            return "El orderColumn solo puede ser: " + com.ordenaris.restaurant.RestaurantConstants.ALLOWED_SORT_COLUMNS.join(', ')
        }
        if (!params.order) {
            return "El order no puede ir vacío"
        }
        def orderDir = params.order?.toString()?.trim()
        if (!com.ordenaris.restaurant.RestaurantConstants.ALLOWED_ORDER.contains(orderDir)) {
            return "El order solo puede ser: " + com.ordenaris.restaurant.RestaurantConstants.ALLOWED_ORDER.join(', ')
        }
        if (!params.max) {
            return "El max no puede ir vacio"
        }
        if (!StringUtils.onlyNumbers(params.max?.toString())) {
            return "El max debe contener solo números"
        }
        def allowedPageSizes = com.ordenaris.restaurant.RestaurantConstants.getAllowedPageSizes(grailsApplication)
        if (!(params.max.toInteger() in allowedPageSizes)) {
            return "El max puede ser solo: " + allowedPageSizes.join(', ')
        }
        if (params.menuUuid && !StringUtils.isValidUuid(params.menuUuid?.toString())) {
            return "El menú debe ser un UUID de 32 caracteres"
        }
        return null
    }
    static def validateNewDishData(def data, grailsApplication) {
        def result = validateDishName(data.name, grailsApplication)
        if (result instanceof Map && result.success == false) return result

        result = validateDishCost(data.cost)
        if (result instanceof Map && result.success == false) return result

        result = validateMenuType(data.menuType)
        if (result instanceof Map && result.success == false) return result

        result = validateDishDescription(data.description, grailsApplication)
        if (result instanceof Map && result.success == false) return result

        if (!isEmptyOrTemplateDateValue(data.availableDate)) {
            result = validateAndParseAvailableDate(data.availableDate)
            if (result instanceof Map && result.success == false) return result
        }

        if (data.imageUrl) {
            result = validateImageUrl(data.imageUrl, grailsApplication)
            if (result instanceof Map && result.success == false) return result
        }

        return null
    }

    static def validateDishName(def name, grailsApplication) {
        if (!name || !name.toString().trim()) {
            return [success: false, message: 'El nombre es obligatorio']
        }
        def maxNameLength = grailsApplication.config.restaurant.dish.maxNameLength
        if (name.toString().trim().size() > maxNameLength) {
            return [success: false, message: 'El nombre no puede exceder ' + maxNameLength + ' caracteres']
        }
        if (!StringUtils.onlyLettersAndSpaces(name?.toString())) {
            return [success: false, message: 'El nombre no debe contener caracteres especiales ni números']
        }
    }

    static def validateDishCost(def cost) {
        if (!cost) {
            return [success: false, message: 'El costo es obligatorio']
        }
        def costStr = cost.toString()
        if (!StringUtils.matchesPattern(cost?.toString(), RestaurantConstants.DISH_COST_PATTERN)) {
            return [success: false, message: 'El costo debe ser un número válido']
        }
    }

    static Integer convertAndValidateCost(def cost, grailsApplication) {
        def maxCost = grailsApplication.config.restaurant.dish.maxCost
        BigDecimal decimalCost = cost as BigDecimal
        if (decimalCost > maxCost) {
            throw new IllegalArgumentException('El costo no puede exceder ' + maxCost)
        }
        Integer convertedCost = MoneyUtils.amountToCents(decimalCost)
        return convertedCost
    }

    static Integer validateAvailableDishes(def availableDishes, grailsApplication) {
        if (availableDishes == null) {
            return null
        }

        Integer dishes = availableDishes.toInteger()
        if (availableDishes instanceof String && !StringUtils.onlyNumbers(availableDishes?.toString()) && dishes != -1) {
            throw new IllegalArgumentException('Los platillos disponibles deben ser números o -1 para disponibilidad ilimitada')
        }
        def maxDishes = grailsApplication.config.restaurant.dish.maxAvailableDishes
        if (dishes != -1 && dishes > maxDishes) {
            throw new IllegalArgumentException('Los platillos no pueden exceder ' + maxDishes)
        }
        return dishes
    }

    static def validateMenuType(def menuType) {
        if (!menuType) {
            return [success: false, message: 'El campo tipo de menú es obligatorio']
        }
        if (!StringUtils.isValidUuid(menuType?.toString())) {
            return [success: false, message: 'El UUID del tipo de menú es inválido']
        }
    }

    static def validateDishDescription(def description, grailsApplication) {
        if (!description || !description.toString().trim()) {
            return [success: false, message: 'La descripción es obligatoria']
        }
        if (!StringUtils.matchesPattern(description?.toString().trim(), RestaurantConstants.DISH_DESCRIPTION_PATTERN)) {
            return [success: false, message: 'La descripción contiene caracteres no permitidos']
        }
        def maxDescriptionLength = grailsApplication.config.restaurant.dish.maxDescriptionLength
        if (description.toString().trim().size() > maxDescriptionLength) {
            return [success: false, message: 'La descripción no puede exceder ' + maxDescriptionLength + ' caracteres']
        }
    }

    static def validateAndParseAvailableDate(def availableDate) {
        if (isEmptyOrTemplateDateValue(availableDate)) {
            return null
        }

        try {
            Date parsed
            def rawValue = availableDate.toString().trim()
            def zoneId = ZoneId.of(Conf.getAppTimezone())

            if (StringUtils.onlyNumbers(rawValue)) {
                parsed = new Date(rawValue as Long)
            } else {
                def dateTimePatterns = [
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                ]

                LocalDateTime localDateTime = null
                for (formatter in dateTimePatterns) {
                    try {
                        localDateTime = LocalDateTime.parse(rawValue, formatter)
                        break
                    } catch (DateTimeParseException ignored) {
                    }
                }

                if (localDateTime != null) {
                    parsed = Date.from(localDateTime.atZone(zoneId).toInstant())
                } else {
                    def localDate = LocalDate.parse(rawValue, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    parsed = Date.from(localDate.atStartOfDay(zoneId).toInstant())
                }
            }

            def parsedDay = parsed.toInstant().atZone(zoneId).toLocalDate()
            def today = LocalDate.now(zoneId)
            if (parsedDay.isBefore(today)) {
                return [success: false, message: 'La fecha de disponibilidad no puede ser una fecha pasada']
            }

            return parsed
        } catch (e) {
            return [success: false, message: 'Formato de fecha inválido']
        }
    }

    static def validateImageUrl(def imageUrl, grailsApplication) {
        def maxImageUrlSize = grailsApplication.config.restaurant.dish.maxImageUrlSize
        if (imageUrl.size() > maxImageUrlSize) {
            return [success: false, message: 'La URL de la imagen no puede exceder ' + maxImageUrlSize + ' caracteres']
        }
        if (!StringUtils.matchesPattern(imageUrl?.toString(), RestaurantConstants.DISH_IMAGE_URL_PATTERN)) {
            return [success: false, message: 'La URL debe ser válida y tener extensión jpg, jpeg, png, gif o webp']
        }
    }

    static Integer parseStatusParam(def statusParam, boolean required = false) {
        if (statusParam == null || (statusParam instanceof String && !statusParam.toString().trim())) {
            if (required) {
                return [success: false, message: 'El estado es requerido']
            }
            return null
        }

        Integer statusValue
        if (statusParam instanceof Number) {
            statusValue = statusParam.intValue()
        } else {
            def statusStr = statusParam.toString().trim()
            if (StringUtils.onlyNumbers(statusStr)) {
                statusValue = statusStr.toInteger()
            } else {
                def statusMap = [
                    'active'  : EntityStatus.ACTIVE,
                    'inactive': EntityStatus.INACTIVE,
                    'deleted' : EntityStatus.DELETED
                ]
                statusValue = statusMap[statusStr.toLowerCase()]
                if (statusValue == null) {
                    return [success: false, message: 'Estado inválido. Valores permitidos: active, inactive, deleted']
                }
            }
        }

        if (!EntityStatus.getValidStatuses().contains(statusValue)) {
            return [success: false, message: 'Estado inválido. Valores permitidos: active, inactive, deleted']
        }
        return statusValue
    }
}
