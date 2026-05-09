package com.ordenaris

import grails.web.context.ServletContextHolder as SCH
 
public class Conf {

    public static Map getSettingsConf() {
        return SCH.servletContext?.getAttribute("Conf") as Map ?: [:]
    }

    public static String getAppTimezone() {
        def conf = getSettingsConf()
        return (conf[Constants.APP_TIMEZONE] ?: "America/Mexico_City").toString()
    }

    public static String findConfiguration( identifier ){
        return getSettingsConf()[identifier]?.toString()
    }
}