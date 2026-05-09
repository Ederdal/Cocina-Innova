package com.ordenaris.security

import grails.plugin.springsecurity.userdetails.GrailsUser
import org.springframework.security.core.GrantedAuthority

class AuthManagerBean extends GrailsUser{

    AuthManagerBean(
            String username,
            String crd,
            boolean enabled,
            boolean accountNonExpired,
            boolean credentialsNonExpired,
            boolean accountNonLocked,
            Collection<GrantedAuthority> authorities,
            long id
        ){
        super(
            username,
            crd,
            enabled,
            accountNonExpired,
            credentialsNonExpired,
            accountNonLocked,
            authorities,
            id
        )
    }

}