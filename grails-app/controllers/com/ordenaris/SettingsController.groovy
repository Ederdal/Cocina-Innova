package com.ordenaris

import grails.plugin.springsecurity.annotation.Secured

class SettingsController {
	static responseFormats = ['json', 'xml']
    def SettingsService
	
    @Secured(['ROLE_ADMIN'])
    def updateInfoDB(){
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger(Log.INFO, logId, "Actualizar settings", "Inicia solicitud.")
        def resp = SettingsService.updateInfoDB(logId)
        return respond( resp.data, status: resp.status )
    }
}
