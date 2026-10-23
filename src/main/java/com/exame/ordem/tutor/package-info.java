@org.springframework.modulith.ApplicationModule(
        displayName = "Tutor IA",
        allowedDependencies = {
                "usuarios", "usuarios::dto",
                "questoes", "questoes::dto",
                "system"
        }
)
package com.exame.ordem.tutor;