package com.ordenaris.shoppingCart
import java.util.UUID
import com.ordenaris.restaurant.Dish
class ShoppingCartItem {
    String uuid = UUID.randomUUID().toString().replaceAll('\\-', '')
    Integer unitPrice 
    Integer quantity
    Boolean status = true // true = activo, false = cancelado/removido
    Date dateCreated
    Date lastUpdated

    // Relaciones
    static belongsTo = [shoppingCart: ShoppingCart, dish: Dish]  

    static constraints = {
        uuid size: 32..32, unique: true
        shoppingCart nullable: false
        dish nullable: false
        unitPrice min: 0, max: 60000, nullable: false
        quantity min: 1, nullable: false
        status nullable: false
        lastUpdated nullable: true
    }

    static mapping = {
        uuid index: "shopping_cart_uuid_idx"
        shoppingCart index: "shopping_cart_item_shopping_cart_idx"
        dish index: "shopping_cart_dish_idx"
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
