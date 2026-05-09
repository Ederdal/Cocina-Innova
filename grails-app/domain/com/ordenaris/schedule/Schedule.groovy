package com.ordenaris.schedule

import com.ordenaris.security.User
import grails.compiler.GrailsCompileStatic
import java.sql.Time

@GrailsCompileStatic
class Schedule {
    String uuid = UUID.randomUUID().toString().replaceAll('\\-', '')
    Time entryTime
    Time exitTime
    boolean isWorking = true

    static belongsTo = [user: User]

    static constraints = {
        user nullable: false, unique: true
        entryTime nullable: false
        exitTime nullable: false
    }

    static mapping = {
        entryTime column: 'entry_time', sqlType: 'TIME'
        exitTime  column: 'exit_time',  sqlType: 'TIME'
        
        isWorking column: 'is_working'
        version false
    }
}