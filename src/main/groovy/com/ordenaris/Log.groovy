package com.ordenaris
 
import org.slf4j.Logger
import org.slf4j.LoggerFactory
 
public class Log {
    private static final List<String> SENSITIVE_FIELDS = ['crd', 'newCrd', 'rawCrd']
    private static Logger log = LoggerFactory.getLogger(Log)
    private static final List VALID_LEVELS = ['trace', 'debug', 'info', 'warn', 'error']
    public static final String TRACE = "TRACE" // Información muy detallada, útil para depuración profunda.
    public static final String DEBUG = "DEBUG" // Información de depuración, útil durante el desarrollo.
    public static final String INFO = "INFO" // Información general sobre el funcionamiento de la aplicación.
    public static final String WARN = "WARN" // Advertencias sobre situaciones inesperadas, pero que no impiden el funcionamiento.
    public static final String ERROR = "ERROR" // Errores que han ocurrido y que pueden afectar el funcionamiento.

    public static sanitize(data) {
        if (data == null) return null
        if (data instanceof Map) {
            return data.collectEntries { key, value ->
                if (SENSITIVE_FIELDS.any { key.toLowerCase().contains(it) }) {
                    [key, maskValue(value)]
                } else if (value instanceof Map || value instanceof List) {
                    [key, sanitize(value)]
                } else {
                    [key, value]
                }
            }
        }
        if (data instanceof List) {
            return data.collect { sanitize(it) }
        }
        return data
    }

    private static maskValue(value) {
        if (value == null) return null
        def str = value.toString()
        if (str.size() <= 4) return '****'
        return '*'.multiply(str.size())
    }
 
    public static logger(logLevel, logId, process, description, info = null, res = null) {
        String app = System.getProperty("appNameCocina") ?: "b-cocina"
        def level = logLevel?.toLowerCase()
        if (!(level in VALID_LEVELS)) {
            log.warn("Nivel de log inválido: ${logLevel}. Usando INFO.")
            level = 'info'
        }
        def logInfo = "| $logId | $app | $process | $description"
        if (info) logInfo += " | $info"
        if (res) logInfo += " | $res"      
        log."${level}"(logInfo)
    }
}
