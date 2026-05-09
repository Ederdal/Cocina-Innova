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

// Load external config file if provided via environment (e.g. Render CONFIG_FILE)
if (System.getenv('CONFIG_FILE')) {
    grails.config.locations = ["file:${System.getenv('CONFIG_FILE')}"]
}

// DataSource: prefer environment variables (for Render), fallback to sensible defaults
def env = System.getenv()
dataSource {
    pooled = true
    jmxExport = true
    dbCreate = env['DB_DBCREATE'] ?: 'update'
    driverClassName = env['DB_DRIVER'] ?: 'com.mysql.cj.jdbc.Driver'
    dialect = env['DB_DIALECT'] ?: 'org.hibernate.dialect.MySQLDialect'
    username = env['DB_USER'] ?: 'avnadmin'
    password = env['DB_PASS'] ?: 'change-me'
    url = env['DB_URL'] ?: 'jdbc:mysql://localhost:3306/cocina_innovattia?useSSL=false&useUnicode=yes&characterEncoding=UTF-8'
    properties {
        jmxEnabled = true
        maxWait = 60000
        validationQuery = 'SELECT 1'
        timeBetweenEvictionRunsMillis = 18000
        minEvictableIdleTimeMillis = 600000
        removeAbandoned = true
        removeAbandonedTimeout = 30000
        numTestsPerEvictionRun = 3
        testOnBorrow = true
        testWhileIdle = true
        testOnReturn = true
        maxActive = 100
        maxIdle = 50
        minIdle = 10
        jdbcInterceptors = 'ConnectionState;StatementCache(max=200)'
        defaultTransactionIsolation = java.sql.Connection.TRANSACTION_READ_COMMITTED
    }
}

// Server URL and secrets via env
grails.serverURL = env['GRAILS_SERVER_URL'] ?: 'http://localhost:8080'
grails.plugin.springsecurity.conf.rest.token.storage.jwt.secret = env['JWT_SECRET'] ?: "574ac47ccb36efc596bb6754366ff682469bca50a3cd406681e96ac2e6ed9fda"

// OAuth keys from env (if provided)
grails.plugin.springsecurity.rest.oauth.google.key = env['OAUTH_GOOGLE_KEY'] ?: grails.plugin.springsecurity.rest.oauth.google.key
grails.plugin.springsecurity.rest.oauth.google.secret = env['OAUTH_GOOGLE_SECRET'] ?: grails.plugin.springsecurity.rest.oauth.google.secret