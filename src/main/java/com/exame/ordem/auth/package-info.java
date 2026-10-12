@org.springframework.modulith.ApplicationModule(
        displayName = "Autenticação",
        allowedDependencies = {
                "usuarios", "usuarios::dto",
                "administradores", "administradores::dto",
                "system"
        }
)
package com.exame.ordem.auth;