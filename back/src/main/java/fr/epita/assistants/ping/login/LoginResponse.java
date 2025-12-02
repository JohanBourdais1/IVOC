package fr.epita.assistants.ping.login;

public class LoginResponse {
    public String token;
    public String refreshToken;

    public LoginResponse(String token, String refreshToken) {
        this.token = token;
        this.refreshToken = refreshToken;
    }
}

