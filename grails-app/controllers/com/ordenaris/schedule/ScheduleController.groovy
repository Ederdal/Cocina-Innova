package com.ordenaris.schedule

import com.ordenaris.security.User
import grails.plugin.springsecurity.annotation.Secured
import java.time.LocalTime
import java.sql.Time
import com.ordenaris.Log
import com.ordenaris.TypeError

@Secured(['ROLE_ADMIN', 'ROLE_CHEF'])
class ScheduleController {

    static responseFormats = ['json']
    def scheduleService

    def listAllSchedules() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Listar todos los horarios.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = scheduleService.listAllSchedules(logId)
        respond(responseService.data, status: responseService.status)
    }

    def getScheduleInfo() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Obtener informacion de un horario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = scheduleService.getScheduleInfo(params.uuidUser, logId)
        respond(responseService.data, status: responseService.status)
    }

    def createUserSchedule() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Crear un horario a un usuario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def error = validateTimeRange(logId)
        if (error) {
            return respond(error.data, status: error.status)
        }
        
        def responseService = scheduleService.createUserSchedule(
            params.uuidUser,
            Time.valueOf(request.JSON.entryTime),
            Time.valueOf(request.JSON.exitTime),
            logId
        )

        respond(responseService.data, status: responseService.status)
    }

    def changeWorkingHours() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Cambiar horario laboral.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def error = validateTimeRange(logId)
        if (error) {
            return respond(error.data, status: error.status)
        }

        def responseService = scheduleService.changeWorkingHours(
            params.uuidUser,
            Time.valueOf(request.JSON.entryTime),
            Time.valueOf(request.JSON.exitTime),
            logId
        )

        respond(responseService.data, status: responseService.status)
    }

    def changeAvailability() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Cambiar disponibilidad.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = scheduleService.changeAvailability(params, logId)

        respond(responseService.data, status: responseService.status)
    }

    def deleteSchedule() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Eliminar un horario.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = scheduleService.deleteUserSchedule(params.uuidUser, logId)
        respond(responseService.data, status: responseService.status)
    }

    def isAnyChefAvailable() {
        def logId = UUID.randomUUID().toString().replaceAll('\\-', '')
        Log.logger( Log.INFO, logId, "Consultar si hay algun chef disponible.", "Iniciando la solicitud.", "params: ${params}, JSON: ${request.JSON}")

        def responseService = scheduleService.isAnyChefAvailable(logId)
        respond(responseService.data, status: responseService.status)
    }

    private def validateTimeRange(logId) {
        if (!request.JSON.entryTime) {
            return TypeError.missingParameter("hora entrada", logId)
        }

        if (!request.JSON.exitTime) {
            return TypeError.missingParameter("hora salida", logId)
        }

        if (!(request.JSON.entryTime instanceof String)) {
            return TypeError.incorrectFormat("hora entrada", "una cadena de texto", logId)
        }

        if (!(request.JSON.exitTime instanceof String)) {
            return TypeError.incorrectFormat("hora salida", "una cadena de texto", logId)
        }

        if(!request.JSON.entryTime.isHourFormat()) {
            return TypeError.incorrectFormat("hora entrada", "una fecha en formato de 24 horas valido [HH:mm:ss]", logId)
        }

        if(!request.JSON.exitTime.isHourFormat()) {
            return TypeError.incorrectFormat("hora salida", "una fecha en formato de 24 horas valido [HH:mm:ss]", logId)
        }

        def entryTime = LocalTime.parse(request.JSON.entryTime)
        def exitTime = LocalTime.parse(request.JSON.exitTime)

        if (exitTime.isBefore(entryTime)) {
            return TypeError.invalidData("hora entrada y hora salida", logId)
        }

        return null
    }
}