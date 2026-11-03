package org.example.survey.service;

import org.example.survey.config.AppSettings;
import org.example.survey.database.Db;
import org.example.survey.util.TokenUtil;
import org.springframework.stereotype.Service;

@Service
public class ApiTokenService {

    private final AppSettings settings;

    public ApiTokenService(AppSettings settings) {
        this.settings = settings;
    }

    public String create(int userId) {
        String token = TokenUtil.generate(32);
        String hash = TokenUtil.sha256(token);
        int days = settings.getApiTokenDays();
        Db.inTransaction(conn -> {
            Db.update(conn, "DELETE FROM api_tokens WHERE expires_at < NOW()", Db.NONE);
            Db.update(conn,
                    "INSERT INTO api_tokens (user_id, token_hash, expires_at, created_at) VALUES (?, ?, NOW() + (? * INTERVAL '1 day'), NOW())",
                    ps -> {
                        ps.setInt(1, userId);
                        ps.setString(2, hash);
                        ps.setInt(3, days);
                    });
            return null;
        });
        return token;
    }

    public Integer authenticate(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            return null;
        }
        String hash = TokenUtil.sha256(token.trim());
        return Db.one("SELECT t.user_id FROM api_tokens t JOIN users u ON u.id = t.user_id " +
                        "WHERE t.token_hash = ? AND t.expires_at > NOW() AND u.email_verified = TRUE",
                ps -> ps.setString(1, hash), rs -> rs.getInt(1));
    }

    public String expiresAt(String token) {
        String hash = TokenUtil.sha256(token);
        return Db.one("SELECT TO_CHAR(expires_at, 'YYYY-MM-DD\"T\"HH24:MI:SS') FROM api_tokens WHERE token_hash = ?",
                ps -> ps.setString(1, hash), rs -> rs.getString(1));
    }

    public void revoke(String token) {
        String hash = TokenUtil.sha256(token);
        Db.update("DELETE FROM api_tokens WHERE token_hash = ?", ps -> ps.setString(1, hash));
    }

    public void revokeAll(int userId) {
        Db.update("DELETE FROM api_tokens WHERE user_id = ?", ps -> ps.setInt(1, userId));
    }
}
