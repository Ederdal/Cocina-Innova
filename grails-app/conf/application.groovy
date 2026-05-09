import grails.util.Environment

grails.plugin.springsecurity.userLookup.userDomainClassName = 'com.ordenaris.security.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'com.ordenaris.security.UserRole'
grails.plugin.springsecurity.authority.className = 'com.ordenaris.security.Role'

grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    [pattern: '/',                  access: ['permitAll']],
    [pattern: '/api/login',         access: ['permitAll']],
    [pattern: '/error',             access: ['permitAll']],
    [pattern: '/index',             access: ['permitAll']],
    [pattern: '/login/auth',        access: ['denyAll']],
    [pattern: '/api/logout',        access: ['isAuthenticated()']],
    [pattern: '/api/management/**', access:['ROLE_ADMIN']],
    [pattern: '/api/dish/**',       access: ['isAuthenticated()']],
    [pattern: '/static/docs/**', access:['permitAll']],
    [pattern: '/uploads/**', access:['permitAll']]
]

grails.plugin.springsecurity.filterChain.chainMap = [
    [pattern: '/uploads/**', filters: 'none'],
    [pattern: '/api/login',filters: 'JOINED_FILTERS, -exceptionTranslationFilter, -securityContextPersistenceFilter, -rememberMeAuthenticationFilter'],
    [pattern: '/api/**', filters: 'JOINED_FILTERS, -exceptionTranslationFilter, -authenticationProcessingFilter, -securityContextPersistenceFilter, -rememberMeAuthenticationFilter'],
	[pattern: '/static/docs/**', filters: 'JOINED_FILTERS, -exceptionTranslationFilter, -authenticationProcessingFilter, -securityContextPersistenceFilter, -rememberMeAuthenticationFilter'],
]

// JWT CONFIG
grails.plugin.springsecurity.conf.rest.token.storage.jwt.secret = "574ac47ccb36efc596bb6754366ff682469bca50a3cd406681e96ac2e6ed9fda"
grails.plugin.springsecurity.rest.token.storage.jwt.useSignedJwt = true
grails.plugin.springsecurity.rest.token.storage.jwt.expiration = 28800
grails.plugin.springsecurity.rest.token.generation.jwt.algorithm = 'HS256'

// Use Bearer Token
grails.plugin.springsecurity.rest.token.validation.useBearerToken = true
grails.plugin.springsecurity.rest.token.validation.headerName = 'Authorization'
grails.plugin.springsecurity.rest.token.validation.enableAnonymousAccess = false

//Login por usuario y contraseña
grails.plugin.springsecurity.rest.login.active=true
grails.plugin.springsecurity.rest.login.endpointUrl="/api/login"
grails.plugin.springsecurity.rest.login.useJsonCredentials=true
grails.plugin.springsecurity.rest.login.usernamePropertyName="username"
grails.plugin.springsecurity.rest.login.passwordPropertyName="crd"
grails.plugin.springsecurity.rest.login.failureStatusCode = 401

//Login por google
grails.plugin.springsecurity.rest.oauth.google.client = org.pac4j.oauth.client.Google2Client
grails.plugin.springsecurity.rest.oauth.google.scope = org.pac4j.oauth.client.Google2Client.Google2Scope.EMAIL_AND_PROFILE


//Providers
grails.plugin.springsecurity.providerNames = [
    'customAuthenticationProvider',
    'restAuthenticationProvider',
    'anonymousAuthenticationProvider',
    'rememberMeAuthenticationProvider'
]
