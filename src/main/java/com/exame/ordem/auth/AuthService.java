package com.exame.ordem.auth;

import com.exame.ordem.auth.dto.LoginRequest;
import com.exame.ordem.auth.dto.TokenResponse;

public interface AuthService {
    TokenResponse login(LoginRequest request);
}