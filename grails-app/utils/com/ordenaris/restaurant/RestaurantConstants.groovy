package com.ordenaris.restaurant
import java.util.regex.Pattern

class RestaurantConstants {
    static final List<String> ALLOWED_SORT_COLUMNS = [
        'name',
        'status',
        'dateCreated'
    ]
    static final String DEFAULT_SORT_COLUMN = 'dateCreated'
    static final List<String> ALLOWED_ORDER = ['asc', 'desc']
    static final List<Integer> ALLOWED_PAGE_SIZES = [4, 6, 8, 12, 16, 20]

    static final Pattern DISH_COST_PATTERN = ~/^\d+(\.\d{1,2})?$/
    static final Pattern DISH_DESCRIPTION_PATTERN = ~/^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\s.,!?\-()]+$/
    static final Pattern DISH_FILE_NAME_PATTERN = ~/^[a-zA-Z0-9_\-]+\.(jpg|jpeg|png|gif|webp)$/
    static final Pattern DISH_IMAGE_URL_PATTERN = ~/^(https?:\/\/.+|\/?uploads\/dishes\/[a-zA-Z0-9_\-]+)\.(jpg|jpeg|png|gif|webp)$/


    
    static List<Integer> getAllowedPageSizes(def grailsApplication) {
        def allowedPageSizes = grailsApplication.config.getProperty(
            'restaurant.dish.allowedPageSizes',
            List,
            ALLOWED_PAGE_SIZES
        )
        if (!(allowedPageSizes instanceof List)) {
            allowedPageSizes = [allowedPageSizes]
        }
        return allowedPageSizes.collect { it as Integer }
    }

    static Map validateSortColumn(String sortColumn) {
        if (!sortColumn) {
            return [column: DEFAULT_SORT_COLUMN, warning: null]
        }
        if (ALLOWED_SORT_COLUMNS.contains(sortColumn)) {
            return [column: sortColumn, warning: null]
        } else {
            return [column: DEFAULT_SORT_COLUMN, warning: "Columna de orden no permitida: ${sortColumn}. Se usó '${DEFAULT_SORT_COLUMN}' por defecto."]
        }
    }
}