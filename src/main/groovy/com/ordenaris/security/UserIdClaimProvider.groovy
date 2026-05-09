package com.ordenaris.security

import grails.plugin.springsecurity.rest.token.generation.jwt.CustomClaimProvider
import com.nimbusds.jwt.JWTClaimsSet
import org.springframework.security.core.userdetails.UserDetails
import grails.plugin.springsecurity.rest.oauth.OauthUser
import com.ordenaris.security.AuthManagerBean

class UserIdClaimProvider implements CustomClaimProvider {

    @Override
    void provideCustomClaims(JWTClaimsSet.Builder builder,UserDetails details,String principal,Integer expiration) {
        builder.claim("username", details.username)
        if (details instanceof OauthUser) {
            builder.claim("id",details.id)
        }

        if(details instanceof AuthManagerBean) {
            builder.claim("id",details.id)
        }
    }
}
