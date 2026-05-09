package com.ordenaris.restaurant
import java.util.UUID

class MenuType {
    String uuid = UUID.randomUUID().toString().replaceAll('\\-', '')
    String name
    int status = 1 // 0 = inactive ::: 1 = active ::: 2 = deleted
    String startTime // formato HH:mm
    String endTime   // formato HH:mm
    Date dateCreated
    Date lastUpdated
    
    static belongsTo = [parentType: MenuType]
    static hasMany = [subTypes: MenuType, dishes: Dish]

    static constraints = {
        uuid size: 32..32, unique: true
        name maxSize: 80, blank: false
        lastUpdated nullable: true
        parentType nullable: true
        startTime nullable: true
        endTime nullable: true
    }
    
    static mapping = {
        uuid index: "menu_type_uuid_idx"
        version false
        dateCreated column: "date_created"
        lastUpdated column: "last_updated"
        parentType column: "parent_type_id"
        startTime column: "start_time"
        endTime column: "end_time"
    }

    String toString() {
        return name
    }
}