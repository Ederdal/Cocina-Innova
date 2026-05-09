package com.ordenaris.security

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import grails.compiler.GrailsCompileStatic
import com.ordenaris.enums.RegisterTypeUser
import com.ordenaris.schedule.Schedule
import com.ordenaris.order.CustomerOrder

@GrailsCompileStatic
@EqualsAndHashCode(includes='username')
@ToString(includes='username', includeNames=true, includePackage=false)
class User implements Serializable {

    private static final long serialVersionUID = 1

    String uuid = UUID.randomUUID().toString().replaceAll('\\-', '')
    String username
    String crd
    String email
    String names
    String lastNames
    String profileImagePath   
    RegisterTypeUser registerType = RegisterTypeUser.CREDENTIALS
    boolean enabled = true
    boolean accountExpired
    boolean accountLocked
    boolean passwordExpired
    boolean requestChangeCrd = false

    static hasOne = [schedule:Schedule]
    static hasMany = [customerOrders:CustomerOrder]

    User(String username, String crd, String email, String names, String lastNames, RegisterTypeUser registerType) {
		this()
		this.username = username
		this.crd = crd
		this.email = email
		this.names = names
		this.lastNames = lastNames
		this.registerType = registerType
	}

    Set<Role> getAuthorities() {
        (UserRole.findAllByUser(this) as List<UserRole>)*.role as Set<Role>
    }

    static constraints = {
        crd nullable: false, blank: false, password: true
        username nullable: false, blank: false, unique: true
        email nullable: false, blank: false, unique: true
        names nullable: false, blank: false
        lastNames nullable: false, blank: false
        profileImagePath nullable: true
        schedule nullable: true
    }

    static mapping = {
	    crd column: '`crd`'
        registerType enumType: 'string', length: 14
        version false
    }
}
