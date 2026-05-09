package com.ordenaris.schedule

import grails.gorm.transactions.Transactional
import com.ordenaris.security.User
import java.time.LocalTime
import java.sql.Time
import com.ordenaris.Log
import com.ordenaris.Constants
import com.ordenaris.TypeError

@Transactional
class ScheduleService {

    def listAllSchedules(logId) {
        try {
            Log.logger(Log.INFO, logId, "Listar todos los horarios.", "Servicio para listar todos los horarios.")

            def schedules = Schedule.list().collect {
                mapSchedule(it)
            }

            Log.logger(Log.INFO, logId, "Listar todos los horarios.", "Se consulto la informacion con exito.", null, "returnInformation:${schedules.size()}")
            return [ data: [success: true, data: schedules], status: 200 ]

        } catch(e) {
            Log.logger(Log.ERROR, logId, "Listar todos los horarios.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def createUserSchedule(uuidUser, entry, exit, logId) {
        try {
            Log.logger(Log.INFO, logId, "Crear un horario a un usuario.", "Servicio para crear un horario a un usuario.", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}")

            def user = User.findByUuid(uuidUser)
            if (!user) {
                Log.logger(Log.WARN, logId, "Crear un horario a un usuario.", "Usuario no encontrado.", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}")
                return TypeError.informationNotFound(logId)
            }

            if (!user.getAuthorities()*.authority.contains(Constants.ROLE_CHEF)) {
                Log.logger(Log.WARN, logId, "Crear un horario a un usuario.", "Solo los usarios con rol de chef pueden contar con un horario", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}")
                return TypeError.externalPermissionMissing(logId, "ROLE_CHEF")
            }

            if (user.schedule){
                Log.logger(Log.WARN, logId, "Crear un horario a un usuario.", "El usuario ya cuenta con un horario asignado", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}")
                return TypeError.existingRegister(logId)
            }

            user.schedule = new Schedule(
                user: user,
                entryTime: entry,
                exitTime: exit
            ).save(flush: true, failOnError: true)

            Log.logger(Log.INFO, logId, "Crear un horario a un usuario.", "Horario creado exitosamente.", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}", "user: [uuid: ${user.uuid}, schedule: ${user.schedule}]")
            return [ data: [success: true], status: 201 ]

        } catch(e) {
            Log.logger(Log.ERROR, logId, "Crear un horario a un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def changeWorkingHours(uuidUser, entry, exit, logId) {
        try {
            Log.logger(Log.INFO, logId, "Cambiar horario laboral.", "Servicio para cambiar el horario laboral de un chef.", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}")

            def user = User.findByUuid(uuidUser)
            if (!user) {
                Log.logger(Log.WARN, logId, "Cambiar horario laboral.", "Usuario no encontrado.", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}")
                return TypeError.informationNotFound(logId)
            }

            if (!user.schedule){
                Log.logger(Log.WARN, logId, "Cambiar horario laboral.", "El usuario no cuenta con un horario asignado.", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}")
                return TypeError.informationNotFound(logId)
            }

            if (user.schedule.entryTime == entry && user.schedule.exitTime == exit){
                Log.logger(Log.WARN, logId, "Cambiar horario laboral.", "El usuario ya tiene exactamente el mismo horario.", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}")
                return TypeError.existingRegister(logId)
            }

            user.schedule.entryTime = entry
            user.schedule.exitTime = exit
            user.schedule.save(flush: true, failOnError: true)

            Log.logger(Log.INFO, logId, "Cambiar horario laboral.", "Se cambio el horario laboral exitosamente.", "uuidUser: ${uuidUser}, entry: ${entry}, exit:${exit}", "user: [uuid: ${user.uuid}, schedule: [entryTime: ${user.schedule.entryTime}, exitTime: ${user.schedule.exitTime}]]")
            return [ data: [success: true], status: 200 ]

        } catch(e) {
            Log.logger(Log.ERROR, logId, "Cambiar horario laboral.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def changeAvailability(params, logId) {
        try {
            Log.logger(Log.INFO, logId, "Cambiar disponibilidad.", "Servicio para cambiar la disponibilidad de un chef.", "uuidUser: ${params.uuidUser}, status: ${params.status}")

            def user = User.findByUuid(params.uuidUser)
            if (!user) {
                Log.logger(Log.WARN, logId, "Cambiar disponibilidad.", "Usuario no encontrado.", "uuidUser: ${params.uuidUser}, status: ${params.status}")
                return TypeError.informationNotFound(logId)
            }

            if (!user.schedule){
                Log.logger(Log.WARN, logId, "Cambiar disponibilidad.", "El usuario no cuenta con un horario asignado.", "uuidUser: ${params.uuidUser}, status: ${params.status}")
                return TypeError.informationNotFound(logId)
            }
            
            def isWorking = params.status.equals("working")

            if (user.schedule.isWorking == isWorking) {
                Log.logger(Log.WARN, logId, "Cambiar disponibilidad.", "El usuario ya cuenta con el estatus solicitado.", "uuidUser: ${params.uuidUser}, status: ${params.status}, isWorking: ${user.schedule.isWorking}")
                return TypeError.existingRegister(logId)
            }

            user.schedule.isWorking = isWorking
            user.schedule.save(flush: true, failOnError: true)

            Log.logger(Log.INFO, logId, "Cambiar disponibilidad.", "Se cambio el status con exito.", "uuidUser: ${params.uuidUser}, status: ${params.status}", "isWorking: ${user.schedule.isWorking}")
            return [ data: [success: true], status: 200 ]

        } catch(e) {
            Log.logger(Log.ERROR, logId, "Cambiar disponibilidad.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def getScheduleInfo(uuidUser, logId) {
        try {
            Log.logger(Log.INFO, logId, "Obtener informacion de un horario.", "Servicio para obtener la información de un horario.", "uuidUser: ${uuidUser}")

            def user = User.findByUuid(uuidUser)
            if (!user) {
                Log.logger(Log.WARN, logId, "Obtener informacion de un horario.", "Usuario no encontrado.", "uuidUser: ${uuidUser}")
                return TypeError.informationNotFound(logId)
            }

            if (!user.schedule) {
                Log.logger(Log.WARN, logId, "Obtener informacion de un horario.", "El usuario no cuenta con un horario asignado.", "uuidUser: ${uuidUser}")
                return TypeError.informationNotFound(logId)
            }

            Log.logger(Log.INFO, logId, "Obtener informacion de un horario.", "Se consulto la informacion del horario exitosamente.", "uuidUser: ${uuidUser}", "user: [uuid: ${user.uuid}, schedule: [uuid: ${user.schedule.uuid}]]")
            return [ data: [success: true, data: mapSchedule(user.schedule)], status: 200 ]

        } catch(e) {
            Log.logger(Log.ERROR, logId, "Obtener informacion de un horario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def deleteUserSchedule(uuidUser, logId) {
        try {
            Log.logger(Log.INFO, logId, "Eliminar un horario.", "Servicio para eliminar un horario.", "uuidUser: ${uuidUser}")

            def user = User.findByUuid(uuidUser)
            if (!user) {
                Log.logger(Log.WARN, logId, "Eliminar un horario.", "Usuario no encontrado.", "uuidUser: ${uuidUser}")
                return TypeError.informationNotFound(logId)
            }

            def schedule = user.schedule
            if (!schedule) {
                Log.logger(Log.WARN, logId, "Eliminar un horario.", "El usuario no cuenta con un horario asignado.", "uuidUser: ${uuidUser}")
                return TypeError.informationNotFound(logId)
            }

            user.schedule = null
            schedule.delete(flush: true, failOnError: true)
            user.save(flush: true, failOnError: true)

            Log.logger(Log.INFO, logId, "Eliminar un horario.", "Se elimino el horario con exito.", "uuidUser: ${uuidUser}", "user: [uuid: ${user.uuid}, schedule: ${user.schedule}]")
            return [ data: [success: true], status: 200 ]

        } catch(e) {
            Log.logger(Log.ERROR, logId, "Eliminar un horario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def isAnyChefAvailable(logId) {
        try {
            Log.logger(Log.INFO, logId, "Consultar si hay algun chef disponible.", "Servicio para consultar si hay algun chef disponible en este momento.")

            def now = Time.valueOf(LocalTime.now(java.time.ZoneId.of("America/Mexico_City")))

            def isAnyAvailable = Schedule.createCriteria().count {
                eq("isWorking", true)
                le("entryTime", now)
                ge("exitTime", now)
            } > 0

            Log.logger(Log.INFO, logId, "Consultar si hay algun chef disponible.", "Se consulto la informacion con exito.", null, "isAnyAvailable: ${isAnyAvailable}")
            return [data: [success: true, data: [isAnyAvailable: isAnyAvailable]], status: 200]

        } catch(e) {
            Log.logger(Log.ERROR, logId, "Consultar si hay algun chef disponible.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def mapSchedule(schedule) {
        return [ 
            uuidSchedule : schedule.uuid,
            uuidUser     : schedule.user.uuid,
            entryTime     : schedule.entryTime.toString(),
            exitTime      : schedule.exitTime.toString(),
            isWorking     : schedule.isWorking
        ]
    }

}
