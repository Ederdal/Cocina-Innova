package com.ordenaris.security

import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.rendering.AccessTokenJsonRenderer
import groovy.json.JsonBuilder

@Transactional(readOnly = true)
class CustomAccessTokenJsonRenderer implements AccessTokenJsonRenderer {

    @Override
    String generateJson(AccessToken accessToken) {
        try {
            if (!accessToken) {
                Log.logger(Log.WARN, UUID.randomUUID().toString(), "AccessTokenRenderer", "AccessToken nulo al generar JSON")
                return new JsonBuilder([
                    token_type   : 'Bearer',
                    access_token : null,
                    expires_in   : null,
                    refresh_token: null
                ]).toPrettyString()
            }

            def principal = accessToken.principal
            Long principalId = null
            if (principal?.id) {
                try {
                    principalId = principal.id as Long
                } catch(e) {
                    principalId = null
                }
            }

            User user = principalId ? User.get(principalId) : null

            if (!user) {
                return new JsonBuilder([
                    token_type   : 'Bearer',
                    access_token : accessToken.accessToken,
                    expires_in   : accessToken.expiration,
                    refresh_token: accessToken.refreshToken
                ]).toPrettyString()
            }

            // Extraer roles de forma segura
            def roles = []
            try {
                def auths = principal?.authorities
                if (auths instanceof Collection) {
                    roles = auths.collect { it?.authority }.findAll { it }
                } else if (auths) {
                    roles = [auths.authority]?.findAll{it}
                }
            } catch(e) {
                roles = []
            }

            def originalObject = [
                username      : user.username,
                roles         : roles,
                token_type    : 'Bearer',
                access_token  : accessToken.accessToken,
                expires_in    : accessToken.expiration,
                refresh_token : accessToken.refreshToken
            ]

            return new JsonBuilder(originalObject).toPrettyString()

        } catch(Exception e) {
            Log.logger(Log.ERROR, UUID.randomUUID().toString(), "AccessTokenRenderer", "Error al generar JSON de token", e.class.simpleName, e.message)
            return new JsonBuilder([
                token_type   : 'Bearer',
                access_token : accessToken?.accessToken,
                expires_in   : accessToken?.expiration,
                refresh_token: accessToken?.refreshToken
            ]).toPrettyString()
        }
    }
}
