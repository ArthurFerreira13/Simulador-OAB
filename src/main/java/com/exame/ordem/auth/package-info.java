@org.springframework.modulith.ApplicationModule(
        displayName = "Autenticação",
        allowedDependencies = {
                "usuarios", "usuarios::dto",
                "system"
        }
)
package com.exame.ordem.auth;