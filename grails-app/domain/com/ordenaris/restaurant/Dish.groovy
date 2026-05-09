package com.ordenaris.restaurant
import java.util.UUID
import com.ordenaris.order.OrderItem

class Dish {
    String uuid = UUID.randomUUID().toString().replaceAll('\\-', '')
    String name
    int status = 1 // 0 = inactive ::: 1 = active ::: 2 = deleted
    Date dateCreated
    Date lastUpdated
    Date availableDate  
    int cost
    String description
    int availableDishes = -1
    String imageUrl
    Date dateImageUploaded
    Date lastImageModified

    static belongsTo = [menuType: MenuType]

    static hasMany = [orderItems: OrderItem]

    static constraints = {
        cost range: 0..60000
        description maxSize: 100
        uuid size: 32..32, unique: true
        name maxSize: 80, unique: false
        lastUpdated nullable: true
        availableDishes nullable: true
        availableDate nullable: true
        imageUrl nullable: true, maxSize: 500
        dateImageUploaded nullable: true
        lastImageModified nullable: true
    }
    
    static mapping = {
        uuid index: "dish_uuid_idx"
        version false
        dateCreated column: "date_created"
        lastUpdated column: "last_updated"
        availableDate column: "available_date"
        availableDishes column: "available_dishes"
        menuType column: "menu_type_id"
        dateImageUploaded column: "date_image_uploaded"
        lastImageModified column: "last_image_modified"
    }

    String toString() {
        return name
    }
}