package com.ordenaris
 
import org.grails.web.servlet.mvc.OutputAwareHttpServletResponse
 
public class TypeError {
 
    static HashMap missingParameter(String data, String logId) {
        return [data: [success:false, message: String.format("Es necesario enviar el dato %s.", data), id:logId], status: 400];
    }
 
    static HashMap missingParameter(String data, String logId, OutputAwareHttpServletResponse response) {
        response.setStatus(400)
        return [success:false, message: String.format("Es necesario enviar el dato %s.", data), id:logId];
    }
   
    static HashMap incorrectFormat(String data, String format, String logId) {
        return [data: [success:false, message: String.format("El dato %s tiene un formato incorrecto. Debe ser %s.", data, format), id:logId], status: 400];
    }
 
    static HashMap incorrectFormat(String data, String format, String logId, OutputAwareHttpServletResponse response) {
        response.setStatus(400)
        return [success:false, message: String.format("El dato %s tiene un formato incorrecto. Debe ser %s.", data, format), id:logId];
    }

    static HashMap contentTooLarge(String logId, OutputAwareHttpServletResponse response) {
        response.setStatus(413)
        return [success:false, message: String.format("El tamaño maximo de carga es de 10MB."), id:logId];
    }
 
    static HashMap noPermissions(String logId) {
        return [data: [success:false, message: String.format("No cuenta con permisos para acceder a este recurso."), id:logId], status: 401];
    }
 
    static HashMap noPermissions(String logId, OutputAwareHttpServletResponse response) {
        response.setStatus(401)
        return [success:false, message: String.format("No cuenta con permisos para acceder a este recurso."), id:logId];
    }
 
    static HashMap informationNotFound(String logId) {
        return [data: [success:false, message: String.format("No se encontró la información solicitada."), id:logId], status: 412];
    }
 
    static HashMap internalError(String logId) {
        return [data: [success:false, message: String.format("Se ha producido un error interno. Inténtelo de nuevo más tarde."), id:logId], status: 500];
    }
 
    static HashMap invalidData(String data, String logId) {
        return [data: [success:false, message: String.format("El dato %s es inválido.", data).toString(), id:logId], status: 403];
    }
 
    static HashMap invalidData(String data, String logId, OutputAwareHttpServletResponse response) {
        response.setStatus(403)
        return [success:false, message: String.format("El dato %s es inválido.", data), id:logId];
    }
 
    static HashMap apiLoginFailed(String logId) {
        return [data: [success:false, message: String.format("No se obtuvieron los permisos para acceder a este recurso."), id:logId], status: 424];
    }
 
    static HashMap externalApiFailure(String message, String logId) {
        return [data: [success:false, message: message ? message : "Se ha producido un error de conexión.", id:logId], status: 424];
    }
 
    static HashMap exceededAttempts( String logId, Integer horas = null) {
        if(horas==null){
            return [data: [success:false, message: String.format("Ha superado el máximo de intentos permitidos. Inténtalo más tarde."), id:logId], status: 429];
        }
        return [data: [success:false, message: String.format("Ha superado el máximo de intentos permitidos. Inténtalo en: %s horas", horas ), id:logId], status: 429];
    }
 
    static HashMap excessTime(String logId) {
        return [data: [success:false, message: String.format("El código que has introducido ha expirado."), id:logId], status: 410];
    }
 
    static HashMap existingRegister(String logId, String message = null) {
        if (message) {
            return [data: [success:false, message: String.format("%s.", message), id:logId], status: 409];
        }
        return [data: [success:false, message: String.format("No es posible procesar la solicitud, por favor utilice valores diferentes."), id:logId], status: 409];
    }
 
    static HashMap excessRegister(String logId) {
        return [data: [success:false, message: String.format("Se ha alcanzado el número máximo de registros permitidos para este recurso."), id:logId], status: 409];
    }
    
    static HashMap conflictByRegisterType(String logId) {
        return [data: [success:false, message: String.format("La cuenta fue registrada con una cuenta de google, por lo cual no es posible realizar esta acción."), id:logId], status: 409];
    }
 
    static HashMap preconditionRequired(String logId) {
        return [data: [success:false, message: String.format("No se ha realizado una solicitud previa para realizar esta acción."), id:logId], status: 428];
    }
 
    static HashMap relationshipConflict(String logId) {
        return [data: [success:false, message: String.format("No se puede realizar esta accion debido a que cuenta con registros asociados."), id:logId], status: 409];
    }
 
    static HashMap externalPermissionMissing(String logId, String data) {
        return [data: [success:false, message: String.format("Solo es posible realizar esta acción sobre usuarios con %s.", data), id:logId], status: 412];
    }

    static HashMap permissionMissing(String data, String logId) {
        return [data: [success:false, message: String.format("Solo usuarios con rol %s pueden acceder a este recurso.", data), id:logId], status: 403];
    }
    
    static HashMap resourceNotAvailable(String data, String logId) {
        return [data: [success:false, message: String.format("No es posible realizar esta accion temporalmente debido a %s.", data), id:logId], status: 410];
    }
 
    static HashMap resourceNotAvailable(String data, String logId, OutputAwareHttpServletResponse response) {
        response.setStatus(410)
        return [success:false, message: String.format("No es posible realizar esta accion temporalmente debido a %s.", data), id:logId];
    }
    
    static HashMap permissionCustom(String message, String logId) {
        return [data: [success:false, message: message, id:logId], status: 403];
    }

    static HashMap orderAlreadyCancelled(String logId) {
        return [data: [success:false, message: "No es posible realizar esta acción porque la orden ya fue cancelada previamente.", id:logId], status: 409];
    }

    static HashMap orderAlreadyFinalized(String logId) {
        return [data: [success:false, message: "No es posible realizar esta acción porque la orden ya fue finalizada.", id:logId], status: 409];
    }

}
