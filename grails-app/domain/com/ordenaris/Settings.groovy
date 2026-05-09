package com.ordenaris

class Settings {

    String identifier
    String data
 
    static constraints = {
        identifier maxSize: 70, blank: false, unique: true
        data nullable: false
    }
    static mapping = {
        version false
        identifier comment: "**Descripción: Código o clave que identifica de manera única los datos. **Tipo: Alfanumérico. **Dominio: Alfabeto, número y caracteres especiales. **Composición: { A-Z | a-z | 0-9 | Caracteres especiales }"
        data sqlType: 'text', comment: "**Descripción: Valores asociados al identificador correspondiente. **Tipo: Alfanumérico. **Dominio: Alfabeto, número y caracteres especiales. **Composición: { A-Z | a-z | 0-9 | Caracteres especiales }"
         
    }
}
