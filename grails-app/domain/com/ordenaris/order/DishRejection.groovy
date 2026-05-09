package com.ordenaris.order

import java.util.UUID
import com.ordenaris.security.User

class DishRejection {
    String uuid = UUID.randomUUID().toString().replaceAll('\\-', '')
    String reason
    Date dateCreated
    Date dateApproved
    String approvalStatus = "Pending" // Pending, Approved, Cancelled

    static belongsTo = [
        order: CustomerOrder,
        orderItem: OrderItem,
        chef: User
    ]

    static constraints = {
        uuid size: 32..32, unique: true
        reason blank: false, maxSize: 500
        order nullable: false
        orderItem nullable: false
        chef nullable: false
        approvalStatus inList: ["Pending", "Approved", "Cancelled"], blank: false
        dateApproved nullable: true
    }

    static mapping = {
        uuid index: "dish_rejection_uuid_idx"
        order index: "dish_rejection_order_idx"
        chef index: "dish_rejection_chef_idx"
        version false
        dateCreated column: "date_created"
    }

    String toString() {
        return "DishRejection ${uuid} - ${approvalStatus}"
    }
}
