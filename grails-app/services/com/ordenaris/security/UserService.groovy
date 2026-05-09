package com.ordenaris.security

import grails.gorm.transactions.Transactional
import grails.util.Holders
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import com.ordenaris.enums.RegisterTypeUser
import com.ordenaris.Log
import com.ordenaris.TypeError
import com.ordenaris.Conf
import com.ordenaris.Constants

@Transactional
class UserService {

    def grailsApplication = Holders.grailsApplication

    def users = { params, orderColumn = null, sort = null ->
        if (params.enabled) {
            eq("enabled", params.enabled.toBoolean())
        }
        if (params.locked) {
            eq("accountLocked", params.locked.toBoolean())
        }
        if (params.query) {
            or {
                like("username", "%${params.query}%")
                like("email", "%${params.query}%")
            }
        }

        if(sort && orderColumn){
            order(orderColumn, sort)
        }
    }

    def registerUser(data, logId) {
        try {
            Log.logger( Log.INFO, logId, "Registrar nuevo usuario.", "Servicio para registrar un nuevo usuario.", "data: ${Log.sanitize(data)}")

            if (User.findByUsername(data.username)) {
                Log.logger( Log.WARN, logId, "Registrar nuevo usuario.", "Ya existe un usuario con el mismo nombre de usuario.", "data: ${Log.sanitize(data)}")     
                return TypeError.existingRegister(logId, "Ya existe un usuario con este nombre de usuario")
            }

            def configValue = Conf.findConfiguration(Constants.VALID_EMAIL_DOMAINS)
            if (!configValue) {
                Log.logger(Log.ERROR, logId, "Registrar nuevo usuario.", "Configuración VALID_EMAIL_DOMAINS no encontrada.", "data: ${Log.sanitize(data)}")
                return TypeError.internalError(logId)
            }

            def validEmailDomains = configValue.split(",").toList()*.trim()
            if (!validEmailDomains.any { data.email.endsWith(it) }) {
                Log.logger( Log.WARN, logId, "Registrar nuevo usuario.", "El dominio del email no es valido.", "data: ${Log.sanitize(data)}")
                return TypeError.incorrectFormat("correo", "un correo con uno de los siguientes dominios ${validEmailDomains}", logId)
            }

            if (User.findByEmail(data.email)) {
                Log.logger( Log.WARN, logId, "Registrar nuevo usuario.", "Ya existe un usuario con el mismo correo electronico.", "data: ${Log.sanitize(data)}" )     
                return TypeError.existingRegister(logId, "Ya existe un usuario con este correo electrónico")
            }

            def user = new User(
                data.username,
                data.crd,
                data.email,
                data.names,
                data.lastNames,
                RegisterTypeUser.CREDENTIALS
            )

            user.accountLocked = true
            user.save(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Registrar nuevo usuario.", "Usuario registrado correctamente.", "data: ${Log.sanitize(data)}", "Nuevo usuario: ${user}")
            return [ data: [success: true], status:201 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Registrar nuevo usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def paginateUsers(params, logId) {
        try {
            Log.logger( Log.INFO, logId, "Paginar usuarios.", "Servicio para listar usuarios.", "params: ${params}")
            
            def page = params.page.toInteger()
            def max = params.max.toInteger()
            def offset = page * max

            def listUsers = User.createCriteria().list(max: max, offset: offset, users.curry(params, params.orderColumn, params.order))
            .collect { user ->
                mapUser(user)
            }

            def totalUsers = User.createCriteria().count(users.curry(params))

            Log.logger( Log.INFO, logId, "Paginar usuarios.", "Listado completado.", "params: ${params}", "returnInformation: ${listUsers.size()}" )
            return [ data: [success: true, data: [list: listUsers, total: totalUsers]], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Paginar usuarios.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def changeStatus(params, currentUser, logId) {
        try {
            Log.logger( Log.INFO, logId, "Cambiar status.", "Servicio para cambiar status de usuarios.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]")

            def user = User.findByUuid(params.uuid)
            if (!user) {
                Log.logger( Log.WARN, logId, "Cambiar status.", "Usuario no encontrado.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]")
                return TypeError.informationNotFound(logId)
            } 

            if (params.status.equals("active") || params.status.equals("deactivate")) {

                if (!currentUser.authorities*.authority.contains(Constants.ROLE_FINANCE)) {
                    Log.logger( Log.WARN, logId, "Cambiar status.", "Solo usuarios de finanzas pueden activar/desactivar la posibilidad de realizar ordenes a los usuarios.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]")
                    return TypeError.permissionMissing("[ROLE_FINANCE]", logId)
                }

                def status = params.status.equals("active")

                if (user.enabled == status) {
                    Log.logger( Log.WARN, logId, "Cambiar status.", "El usuario ya tiene el status solicitado.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]", "enabled: ${user.enabled}")
                    return TypeError.existingRegister(logId)
                } 
                
                user.enabled = status
                user.save(flush: true, failOnError: true)

                Log.logger( Log.INFO, logId, "Cambiar status.", "Se cambio el status de la cuenta con exito.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]", "enabled: ${user.enabled}")
                return [ data: [success: true, data: "La cuenta de " + user.names + " " + user.lastNames + " ha sido " + (user.enabled ? "activada" : "desactivada")], status: 200 ]
            }

            if (!currentUser.authorities*.authority.contains(Constants.ROLE_ADMIN)) {
                Log.logger( Log.WARN, logId, "Cambiar status.", "Solo administradores pueden bloquear o desbloquear usuarios.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]")
                return TypeError.permissionMissing("[ROLE_ADMIN]", logId)
            }

            if (user.id == currentUser.id) {
                Log.logger( Log.WARN, logId, "Cambiar status.", "Un administrador no puede bloquear/desbloquear su propia cuenta.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]", "accountLocked: ${user.accountLocked}")     
                return TypeError.resourceNotAvailable("que un administrador no puede bloquear/desbloquear su propia cuenta", logId)
            }

            def status = params.status.equals("block")
            if (status && user.username == Constants.ADMIN) {
                Log.logger( Log.WARN, logId, "Cambiar status.", "No es posible bloquear esta cuenta.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]", "accountLocked: ${user.accountLocked}")     
                return TypeError.resourceNotAvailable("que este usuario es el super administrador", logId)
            }

            if (user.accountLocked == status) {
                Log.logger( Log.WARN, logId, "Cambiar status.", "El usuario ya tiene el status solicitado.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]", "accountLocked: ${user.accountLocked}")     
                return TypeError.existingRegister(logId)
            } 

            user.accountLocked = status
            user.save(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Cambiar status.", "Se cambio el status de la cuenta con exito.", "params: ${params}, currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]", "accountLocked: ${user.accountLocked}")
            return [ data: [success: true, data: "La cuenta de " + user.names + " " + user.lastNames + " ha sido " + (user.accountLocked ? "bloqueada" : "desbloqueada")], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Cambiar status.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def getUserInfo(currentUser, logId) {
        try {
            Log.logger( Log.INFO, logId, "Obtener información de un usuario.", "Se consulto la información del usuario exitosamente.", "currentUser: [uuid: ${currentUser.uuid}, username: ${currentUser.username}]")
            return [ data: [success: true, data: mapUser(currentUser)], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Obtener información de un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def adminChangeCrd(uuid, rawCrd, logId) {
        try {
            Log.logger( Log.INFO, logId, "Cambiar crd de un usuario.", "Servicio para cambiar crd de un usuario.", "uuid: ${uuid}")

            def user = User.findByUuid(uuid)
            if (!user) {
                Log.logger( Log.WARN, logId, "Cambiar crd de un usuario.", "Usuario no encontrado.", "uuid: ${uuid}")
                return TypeError.informationNotFound(logId)
            }

            if (user.registerType == RegisterTypeUser.GOOGLE) {
                Log.logger( Log.WARN, logId, "Cambiar crd de un usuario.", "La cuenta fue registrada con una cuenta de google, por lo cual no es posible cambiar su crd.", "uuid: ${uuid}")
                return TypeError.conflictByRegisterType(logId)
            }

            if (!user.requestChangeCrd) {
                Log.logger( Log.WARN, logId, "Cambiar crd de un usuario.", "La cuenta no ha solicitado un cambio de crd.", "uuid: ${uuid}")
                return TypeError.preconditionRequired(logId)
            }

            user.crd = rawCrd
            user.requestChangeCrd = false

            user.save(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Cambiar crd de un usuario.", "Se cambio el crd con exito.", "uuid: ${uuid}")
            return [ data: [success: true], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Cambiar crd de un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def changeProfilePicture(user, file, logId) {
        try {
            Log.logger( Log.INFO, logId, "Cambiar foto de perfil.", "Servicio para cambiar foto de perfil personal.", "user: [uuid: ${user.uuid}, username: ${user.username}], fileExtension: ${extractExtension(file.originalFilename)}")

            def profileDir = Paths.get(grailsApplication.config.repository, 'uploads/profile')
            Files.createDirectories(profileDir)

            def extension = extractExtension(file.originalFilename)
            def filename = "user_${user.uuid}${extension}"

            def targetPath = profileDir.resolve(filename)

            file.transferTo(targetPath.toFile())

            user.profileImagePath = "uploads/profile/${filename}"
            user.save(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Cambiar foto de perfil.", "Se cambio la foto de perfil personal con exito.", "user: [uuid: ${user.uuid}, username: ${user.username}], fileExtension: ${extractExtension(file.originalFilename)}")
            return [ data: [success: true, data: "Se guardo con exito la foto de perfil"], status: 200 ]
            
        } catch(e) {
            Log.logger( Log.ERROR, logId, "Cambiar foto de perfil.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def getProfilePicture(user, logId) {
        try {
            Log.logger( Log.INFO, logId, "Obtener foto de perfil.", "Servicio para obtener foto de perfil personal.", "user: [uuid: ${user.uuid}, username: ${user.username}]")

            if (user.profileImagePath) {
                def pathImage = Paths.get(grailsApplication.config.repository, user.profileImagePath)
                if (Files.exists(pathImage)) {
                    Log.logger( Log.INFO, logId, "Obtener foto de perfil.", "Se consulto la foto de perfil personal con exito.", "user: [uuid: ${user.uuid}, username: ${user.username}]", "fileExtension: ${extractExtension(pathImage.toString())}")
                    return [ data: [success: true, data: [image: pathImage.toFile()]], status: 200 ]
                }
            }

            def pathDefaultImage = Paths.get(grailsApplication.config.repository, 'uploads/profile/default.png')

            Log.logger( Log.INFO, logId, "Obtener foto de perfil.", "El usuario no cuenta con una foto de perfil personal, se regreso la imagen base.", "user: [uuid: ${user.uuid}, username: ${user.username}]", "fileExtension: ${extractExtension(pathDefaultImage.toString())}")
            return [ data: [success: true, data: [image: pathDefaultImage.toFile()]], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Obtener foto de perfil.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def changeUserProfilePicture(uuid, file, logId) {
        try {
            Log.logger( Log.INFO, logId, "Cambiar foto de perfil de un usuario.", "Servicio para cambiar la foto de perfil de un usuario.", "uuid: ${uuid}, fileExtension: ${extractExtension(file.originalFilename)}")

            def user = User.findByUuid(uuid)
            if (!user) {
                Log.logger( Log.WARN, logId, "Cambiar foto de perfil de un usuario.", "Usuario no encontrado.", "uuid: ${uuid}, fileExtension: ${extractExtension(file.originalFilename)}")
                return TypeError.informationNotFound(logId)
            }

            def profileDir = Paths.get(grailsApplication.config.repository, 'uploads/profile')
            Files.createDirectories(profileDir)

            def extension = extractExtension(file.originalFilename)
            def filename = "user_${user.uuid}${extension}"

            def targetPath = profileDir.resolve(filename)

            file.transferTo(targetPath.toFile())

            user.profileImagePath = "uploads/profile/${filename}"
            user.save(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Cambiar foto de perfil de un usuario.", "Se cambio la foto de perfil de un usuario con exito.", "uuid: ${uuid}, fileExtension: ${extractExtension(file.originalFilename)}")
            return [ data: [success: true, data: "Se guardó con éxito la foto de perfil."], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Cambiar foto de perfil de un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def getUserProfilePicture(uuid, logId) {
        try {
            Log.logger( Log.INFO, logId, "Obtener foto de perfil de un usuario.", "Servicio para obtener la foto de perfil de un usuario.", "uuid: ${uuid}")

            def user = User.findByUuid(uuid)
            if (!user) {
                Log.logger( Log.WARN, logId, "Obtener foto de perfil de un usuario.", "Usuario no encontrado.", "uuid: ${uuid}")
                return TypeError.informationNotFound(logId)
            }

            if (user.profileImagePath) {
                def pathImage = Paths.get(grailsApplication.config.repository, user.profileImagePath)
                if (Files.exists(pathImage)) {
                    Log.logger( Log.INFO, logId, "Obtener foto de perfil de un usuario.", "Se consulto la foto de perfil del usuario con exito.", "uuid: ${uuid}", "fileExtension: ${extractExtension(pathImage.toString())}")
                    return [ data: [success: true, data: [image: pathImage.toFile()]], status: 200 ]
                }
            }

            def pathDefaultImage = Paths.get(grailsApplication.config.repository, 'uploads/profile/default.png')

            Log.logger( Log.INFO, logId, "Obtener foto de perfil de un usuario.", "El usuario no cuenta con una foto de perfil, se regreso la imagen base.", "uuid: ${uuid}", "fileExtension: ${extractExtension(pathDefaultImage.toString())}")
            return [ data: [success: true, data: [image: pathDefaultImage.toFile()]], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Obtener foto de perfil de un usuario.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def updateChangeCrdRequest(currentUser, status, email, logId){
        try {
            Log.logger( Log.INFO, logId, "Actualizar la solicitud de cambio de crd.", "Servicio para actualizar la solicitud de cambio de crd.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}")

            if (!currentUser) {
                def configValue = Conf.findConfiguration(Constants.VALID_EMAIL_DOMAINS)
                if (!configValue) {
                    Log.logger(Log.ERROR, logId, "Actualizar la solicitud de cambio de crd.", "Configuración VALID_EMAIL_DOMAINS no encontrada.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}")
                    return TypeError.internalError(logId)
                }

                def validEmailDomains = configValue.split(",").toList()*.trim()
                if (!validEmailDomains.any { email.endsWith(it) }) {
                    Log.logger( Log.WARN, logId, "Actualizar la solicitud de cambio de crd.", "El dominio del email no es valido.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}")
                    return TypeError.invalidData("correo", logId)
                }

                def user = User.findByEmail(email)
                if (!user) {
                    Log.logger( Log.WARN, logId, "Actualizar la solicitud de cambio de crd.", "Usuario no encontrado.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}")
                    return TypeError.invalidData("correo", logId)
                }

                if (user.registerType == RegisterTypeUser.GOOGLE) {
                    Log.logger( Log.WARN, logId, "Actualizar la solicitud de cambio de crd.", "La cuenta fue registrada con una cuenta de google, por lo cual no es posible continuar con la solicitud.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}")
                    return TypeError.invalidData("correo", logId)
                }

                if (user.requestChangeCrd == status) {
                    Log.logger( Log.WARN, logId, "Actualizar la solicitud de cambio de crd.", "Ya se realizo la solicitud/cancelación de cambio de crd.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}")     
                    return TypeError.existingRegister(logId, "Ya se registró la solicitud de cambio de contraseña. Acércate con un administrador para continuar con el proceso")
                }

                user.requestChangeCrd = status
                user.save(flush: true, failOnError: true)

                Log.logger( Log.INFO, logId, "Actualizar la solicitud de cambio de crd.", "Se actualizo la solicitud de cambio de crd.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}", "requestChangeCrd: ${user.requestChangeCrd}")
                return [ data: [success: true, message: "Solicitaste el cambio de contraseña. Acércate con un administrador para continuar con el proceso."], status: 200 ]
            }

            if (currentUser.registerType == RegisterTypeUser.GOOGLE) {
                Log.logger( Log.WARN, logId, "Actualizar la solicitud de cambio de crd.", "La cuenta fue registrada con una cuenta de google, por lo cual no es posible continuar con la solicitud.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}")
                return TypeError.conflictByRegisterType(logId)
            }

            if (currentUser.requestChangeCrd == status) {
                Log.logger( Log.WARN, logId, "Actualizar la solicitud de cambio de crd.", "Ya se realizo la solicitud/cancelacion de cambio de crd.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}")     
                return TypeError.existingRegister(logId)
            }

            currentUser.requestChangeCrd = status
            currentUser.save(flush: true, failOnError: true)

            Log.logger( Log.INFO, logId, "Actualizar la solicitud de cambio de crd.", "Se actualizo la solicitud de cambio de crd.", "currentUser: [uuid: ${currentUser?.uuid}, username: ${currentUser?.username}], status: ${status}, email: ${email}", "requestChangeCrd: ${currentUser.requestChangeCrd}")
            return [ data: [success: true, message: (currentUser.requestChangeCrd ? "Solicitaste" : "Cancelaste") + " el cambio de contraseña"], status: 200 ]

        } catch(e) {
            Log.logger( Log.ERROR, logId, "Actualizar la solicitud de cambio de crd.", "Algo ha salido mal.", "error: ${e.class.simpleName} | message: ${e.getMessage()}", "stacktrace: ${e.stackTrace.take(10).join('\n')}" )
            return TypeError.internalError(logId)
        }
    }

    def mapUser(user) {
        return [
            uuid             : user.uuid,
            username         : user.username,
            email            : user.email,
            names            : user.names,
            lastNames        : user.lastNames,
            enabled          : user.enabled,
            accountLocked    : user.accountLocked,
            requestChangeCrd : user.requestChangeCrd,
            registerType     : user.registerType
        ]
    }

    def extractExtension(filename) {
        return filename.substring(filename.lastIndexOf('.')).toLowerCase()
    }
}
