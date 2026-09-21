@org.springframework.modulith.ApplicationModule(
        displayName = "Simulados",
        allowedDependencies = {
                "questoes", "questoes::dto",
                "usuarios", "usuarios::dto"
        }
)
package com.exame.ordem.simulados;