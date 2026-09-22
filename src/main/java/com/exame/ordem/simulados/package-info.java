@org.springframework.modulith.ApplicationModule(
        displayName = "Simulados",
        allowedDependencies = {
                "questoes", "questoes::dto",
                "usuarios", "usuarios::dto",
                "system"
        }
)
package com.exame.ordem.simulados;