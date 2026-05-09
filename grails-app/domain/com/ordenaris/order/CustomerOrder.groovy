package com.ordenaris.order
import java.util.UUID
import com.ordenaris.security.User
import com.ordenaris.finance.Sale
import java.sql.Time

class CustomerOrder {
    String uuid = UUID.randomUUID().toString().replaceAll('\\-', '')
    String status = "Queue" // Queue, Preparing, Finished
    String commentUser
    String commentChef
    Time orderTime
    Time completedTime
    Date dateCreated
    Date lastUpdated
    static belongsTo = [user: User, chef: User]

    static hasMany = [orderItems: OrderItem]

    static hasOne = [sale: Sale]

    
    static constraints = {
        uuid size: 32..32, unique: true
        user nullable: false
        chef nullable: true 
        orderTime nullable: true
        completedTime nullable: true
        commentUser nullable: true, maxSize: 500
        commentChef nullable: true, maxSize: 500
        status inList: ["Queue", "Preparing", "Finished", "Cancelled"], blank: false
        sale nullable: true
        lastUpdated nullable: true
    }

    static mapping = {
        uuid index: "customer_order_uuid_idx"
        user index: "customer_order_user_idx"
        chef index: "customer_order_chef_idx"
        commentUser "comment_user"
        commentChef "comment_chef"
        version false
        completedTime column: "completed_time"
        orderTime  column: 'order_time',  sqlType: 'TIME'
        dateCreated column: "date_created"
        lastUpdated column: "last_updated"
    }

    String toString() {
        return "Order ${id} - ${status} (${user.username}) Chef: ${chef?.username ?: 'N/A'}"
    }
}

