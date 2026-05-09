package com.ordenaris.shoppingCart
import java.util.UUID
import com.ordenaris.security.User
class ShoppingCart {
    String uuid = UUID.randomUUID().toString().replaceAll('\\-', '')
    String status = "Pending"
    Date dateCreated
    Date lastUpdated

    // Relación con User
    static belongsTo = [user: User]

    static hasMany = [shoppingCartItem: ShoppingCartItem]
    
    static constraints = {
        uuid size: 32..32, unique: true
        user nullable: false
        status inList: ["Pending", "Delete", "Finished"], blank: false
        lastUpdated nullable: true
    }

    static mapping = {
        version false
        uuid index: "shopping_cart_uuid_idx"
        user index: "shopping_cart_user_idx"
        dateCreated column: "date_created"
        lastUpdated column: "last_updated"
    }

    String toString() {
        return "Order ${id} - ${status} (${user.username})"
    }
}
