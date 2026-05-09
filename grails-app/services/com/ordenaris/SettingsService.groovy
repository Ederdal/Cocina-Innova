package com.ordenaris

import grails.gorm.transactions.Transactional
import grails.web.context.ServletContextHolder as SCH

@Transactional
class SettingsService {
    def servletContext = SCH.servletContext

    def updateInfoDB(logId ){
        Settings.withTransaction{
            try{
                Log.logger(Log.INFO, logId, "Obtener Settings", "Llega al servicio." )
                def obj = [:]
                Settings.list().each{
                    obj["${it.identifier}"] = it.data
                }
                servletContext["Conf"] = obj
                Log.logger(Log.INFO, logId, "Obtener toda la información de la base de datos.", "Fin de solicitud. La información ha sido actualizada.")
                return [ data: [ success: true], status: 200 ]
            }catch(e){
                Log.logger(Log.ERROR, logId, "Obtener toda la información de la base de datos.", "Ha ocurrido un error.", e.getMessage() )
                return TypeError.internalError(logId)
            }
        }
    }
    def registerInitData(){
        Settings.withTransaction{
            try{
                if(!Settings.findByIdentifier(Constants.VALID_EMAIL_DOMAINS)) {
                    new Settings( [ identifier: Constants.VALID_EMAIL_DOMAINS, data: "@ordenaris.com,@innovattia.com,@orquestia.com" ] ).save(failOnError: true)
                }
                if(!Settings.findByIdentifier(Constants.CHEF_QUEUE_VISIBLE_LIMIT)) {
                    new Settings( [ identifier: Constants.CHEF_QUEUE_VISIBLE_LIMIT, data: "5" ] ).save(failOnError: true)
                }
                if(!Settings.findByIdentifier(Constants.REVIEW_STATUS_APPROVED)){
                    new Settings([identifier: Constants.REVIEW_STATUS_APPROVED, data: "1"]).save(failOnError: true)
                }
                if(!Settings.findByIdentifier(Constants.CHEF_PREPARING_VISIBLE_LIMIT)) {
                    new Settings( [ identifier: Constants.CHEF_PREPARING_VISIBLE_LIMIT, data: "2" ] ).save(failOnError: true)
                }
                if(!Settings.findByIdentifier(Constants.CHEF_PREPARING_ACTIVE_LIMIT)) {
                    new Settings( [ identifier: Constants.CHEF_PREPARING_ACTIVE_LIMIT, data: "2" ] ).save(failOnError: true)
                }
                if(!Settings.findByIdentifier(Constants.CHEF_FINISHED_VISIBLE_LIMIT)) {
                    new Settings( [ identifier: Constants.CHEF_FINISHED_VISIBLE_LIMIT, data: "100" ] ).save(failOnError: true)
                }
                if(!Settings.findByIdentifier(Constants.CHEF_CANCELLED_VISIBLE_LIMIT)) {
                    new Settings( [ identifier: Constants.CHEF_CANCELLED_VISIBLE_LIMIT, data: "100" ] ).save(failOnError: true)
                }
                if(!Settings.findByIdentifier(Constants.APP_TIMEZONE)) {
                    new Settings( [ identifier: Constants.APP_TIMEZONE, data: "America/Mexico_City" ] ).save(failOnError: true)
                }
                if(!Settings.findByIdentifier(Constants.CONSUME_CHART_DAYS)) {
                    new Settings( [ identifier: Constants.CONSUME_CHART_DAYS, data: "14"] ).save(failOnError: true)
                }
            }catch(e){
                Log.logger(Log.ERROR, null, "Inserción de data inicial.", "Ha ocurrido un error.", e.getMessage() )
            }
        }
    }
}
