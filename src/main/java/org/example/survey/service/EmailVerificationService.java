package org.example.survey.service;

import org.example.survey.config.AppSettings;
import org.example.survey.database.Db;
import org.example.survey.model.User;
import org.example.survey.util.TokenUtil;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    private static final int RESEND_SECONDS = 60;

    private final MailService mailService;
    private final AppSettings settings;

    public EmailVerificationService(MailService mailService, AppSettings settings) {
        this.mailService = mailService;
        this.settings = settings;
    }

    public boolean isRequired() {
        return settings.isEmailVerificationRequired();
    }

    private String issueToken(int userId) {
        String token = TokenUtil.generate(32);
        String hash = TokenUtil.sha256(token);
        int hours = settings.getEmailTokenHours();
        Db.inTransaction(conn -> {
            Db.update(conn, "DELETE FROM email_tokens WHERE user_id = ? OR expires_at < NOW()", ps -> ps.setInt(1, userId));
            Db.update(conn,
                    "INSERT INTO email_tokens (user_id, token_hash, expires_at, created_at) VALUES (?, ?, NOW() + (? * INTERVAL '1 hour'), NOW())",
                    ps -> {
                        ps.setInt(1, userId);
                        ps.setString(2, hash);
                        ps.setInt(3, hours);
                    });
            return null;
        });
        return token;
    }

    public boolean sendVerification(User user) {
        String token = issueToken(user.getId());
        String link = settings.getBaseUrl() + "/verify-email?token=" + token;
        String text = "Здравствуйте, " + user.getUsername() + "!\n\n"
                + "Для завершения регистрации в SurveySystem подтвердите адрес электронной почты, перейдя по ссылке:\n"
                + link + "\n\n"
                + "Ссылка действительна " + settings.getEmailTokenHours() + " ч.\n"
                + "Если вы не регистрировались на нашем сайте, просто проигнорируйте это письмо.";
        return mailService.send(user.getEmail(), "Подтверждение email - SurveySystem", text);
    }

    public boolean canResend(int userId) {
        return Db.count("SELECT COUNT(*) FROM email_tokens WHERE user_id = ? AND created_at > NOW() - (? * INTERVAL '1 second')",
                ps -> {
                    ps.setInt(1, userId);
                    ps.setInt(2, RESEND_SECONDS);
                }) == 0;
    }

    public boolean verify(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            return false;
        }
        String hash = TokenUtil.sha256(token.trim());
        Integer userId = Db.one("SELECT user_id FROM email_tokens WHERE token_hash = ? AND expires_at > NOW()",
                ps -> ps.setString(1, hash), rs -> rs.getInt(1));
        if (userId == null) {
            return false;
        }
        Db.inTransaction(conn -> {
            Db.update(conn, "UPDATE users SET email_verified = TRUE, updated_at = NOW() WHERE id = ?", ps -> ps.setInt(1, userId));
            Db.update(conn, "DELETE FROM email_tokens WHERE user_id = ?", ps -> ps.setInt(1, userId));
            return null;
        });
        return true;
    }
}
