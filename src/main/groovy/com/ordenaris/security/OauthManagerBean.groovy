package com.ordenaris.security

import grails.plugin.springsecurity.rest.oauth.OauthUser
import org.springframework.security.core.GrantedAuthority
import org.pac4j.oauth.profile.OAuth20Profile

class OauthManagerBean extends OauthUser {

    Long id  

    OauthManagerBean(
        String username,
        String crd,
        Collection<GrantedAuthority> authorities,
        OAuth20Profile profile,
        Long id
    ) {
        super(username, crd, authorities, profile)
        this.id=id
    }
}
