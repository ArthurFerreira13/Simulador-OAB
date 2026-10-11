package com.exame.ordem.usuarios.internal;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String email;

    @Column(name = "senha_hash")
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "provedor_auth", nullable = false, length = 20)
    private ProvedorAuth provedorAuth;

    @Column(name = "provedor_id")
    private String provedorId;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "data_prova_alvo")
    private LocalDate dataProvaAlvo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now(ZoneOffset.UTC);

    public Usuario() {}

    public static Usuario criarComSenha(String email, String senhaHash, String nome) {
        Usuario u = new Usuario();
        u.email = email;
        u.senhaHash = senhaHash;
        u.provedorAuth = ProvedorAuth.LOCAL;
        u.nome = nome;
        return u;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public ProvedorAuth getProvedorAuth() { return provedorAuth; }
    public String getProvedorId() { return provedorId; }
    public String getNome() { return nome; }
    public LocalDate getDataProvaAlvo() { return dataProvaAlvo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }

    public void alterarNome(String novoNome) {
        this.nome = novoNome;
    }

    public void alterarDataProvaAlvo(LocalDate novaData) {
        this.dataProvaAlvo = novaData;
    }

    public void alterarSenhaHash(String novoSenhaHash) {
        this.senhaHash = novoSenhaHash;
    }
}