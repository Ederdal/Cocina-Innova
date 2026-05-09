package com.ordenaris.restaurant
import com.ordenaris.TypeError


class EntityStatus {
    static final Integer INACTIVE = 0
    static final Integer ACTIVE = 1
    static final Integer DELETED = 2
    
    static final String INACTIVE_STR = 'inactive'
    static final String ACTIVE_STR = 'active'
    static final String DELETED_STR = 'deleted'
    
    static Integer fromString(String status) {
        switch(status?.toLowerCase()?.trim()) {
            case ACTIVE_STR: return ACTIVE
            case INACTIVE_STR: return INACTIVE
            case DELETED_STR: return DELETED
            default: throw new IllegalArgumentException("Status inválido: ${status}. Valores permitidos: active, inactive, deleted") 
        }
    }
    

    static String toString(Integer statusCode) {
        switch(statusCode) {
            case ACTIVE: return ACTIVE_STR
            case INACTIVE: return INACTIVE_STR
            case DELETED: return DELETED_STR
            default: throw new IllegalArgumentException("Status code inválido: ${statusCode}")
        }
    }
    
    
    static List<Integer> getValidStatuses() {
        return [INACTIVE, ACTIVE, DELETED]
    }
}


