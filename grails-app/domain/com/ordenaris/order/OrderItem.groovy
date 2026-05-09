package com.ordenaris.order
import java.util.UUID
import com.ordenaris.restaurant.Dish

class OrderItem {
    String uuid = UUID.randomUUID().toString().replaceAll('\\-', '')
    Integer unitPrice 
    Integer quantity
    Boolean status = true // true = activo, false = cancelado/removido
    Boolean payed = false // true = pagado, false = no pagado
    Date dateCreated
    Date lastUpdated

    // Relaciones
    static belongsTo = [customerOrder: CustomerOrder, dish: Dish]  

    static constraints = {
        uuid size: 32..32, unique: true
        customerOrder nullable: false
        dish nullable: false
        unitPrice min: 0, max: 60000, nullable: false
        quantity min: 1, nullable: false
        status nullable: false
        payed nullable: false
        lastUpdated nullable: true
    }

    static mapping = {
        uuid index: "order_item_uuid_idx"
        customerOrder index: "order_item_customer_order_idx"
        dish index: "order_item_dish_idx"
        version false
        dateCreated column: "date_created"
        lastUpdated column: "last_updated"
        unitPrice column: "unit_price"
        dish column: "dish_id"
    }

    String toString() {
        return "${dish.name} x${quantity} - \$${unitPrice}"
    }
}