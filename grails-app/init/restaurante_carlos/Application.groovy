package restaurante_carlos

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment
import org.springframework.core.env.MapPropertySource
import com.ordenaris.Log

class Application extends GrailsAutoConfiguration implements EnvironmentAware {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }

    @Override
    void setEnvironment(Environment environment) {        
        def activeProfile = environment.activeProfiles?.getAt(0)
        def profilePaths = [
            development: "C:/Users/ord-desaback/keys/Archivo-Copnfiguracion-Externa.properties",
            stage_qa: "/mnt/keys/.cocina-innovattia.properties",
            production: "./mnt/keys/.cocina-innovattia.properties" 
        ]
        def path = profilePaths[activeProfile]
        def configBase = path ? new File(path) : null
        if(configBase?.exists()) {
            def config = new ConfigSlurper().parse(configBase.toURL())
            if (config.appName) System.setProperty("appNameCocina", config.appName.toString())
            Log.logger(Log.INFO, "Environment " + activeProfile, "Connection.", "Loading configuration.", "Success", "path: ${configBase.absolutePath}")
            environment.propertySources.addFirst(new MapPropertySource("externalGroovyConfig", config))
        } else {
            Log.logger(Log.ERROR, "Environment " + activeProfile, "Connection.", "Loading configuration.", "Fail", "path: ${configBase?.absolutePath ?: 'no definido'}")
        }
    }
}
