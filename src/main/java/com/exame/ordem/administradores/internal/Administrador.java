package com.exame.ordem.administradores.internal;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "administradores")
public class Administrador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now(ZoneOffset.UTC);

    protected Administrador() {}

    public static Administrador criar(String email, String senhaHash, String nome) {
        Administrador a = new Administrador();
        a.email = email;
        a.senhaHash = senhaHash;
        a.nome = nome;
        return a;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public String getNome() { return nome; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}