package org.example.survey.service;

import org.example.survey.config.AppSettings;
import org.example.survey.database.Db;
import org.example.survey.model.User;
import org.example.survey.util.Validators;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class AppService {

    private static final String USER_COLUMNS =
            "id, username, email, password, is_admin, email_verified, created_at, updated_at";

    public record Registered(String error, User user) {
        public boolean ok() {
            return error == null;
        }
    }

    public record ProfileResult(String error, boolean emailChanged, User user) {
        public boolean ok() {
            return error == null;
        }
    }

    private final PasswordEncoder passwordEncoder;
    private final AppSettings settings;

    public AppService(PasswordEncoder passwordEncoder, AppSettings settings) {
        this.passwordEncoder = passwordEncoder;
        this.settings = settings;
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password"));
        user.setAdmin(rs.getBoolean("is_admin"));
        user.setEmailVerified(rs.getBoolean("email_verified"));
        user.setCreatedAt(Db.time(rs, "created_at"));
        user.setUpdatedAt(Db.time(rs, "updated_at"));
        return user;
    }

    public User getUserById(int id) {
        return Db.one("SELECT " + USER_COLUMNS + " FROM users WHERE id = ?",
                ps -> ps.setInt(1, id), AppService::mapUser);
    }

    public List<User> getAllUsers() {
        return Db.list("SELECT " + USER_COLUMNS + " FROM users ORDER BY id", Db.NONE, AppService::mapUser);
    }

    public User findByLogin(String login) {
        String value = Validators.clean(login);
        return Db.one("SELECT " + USER_COLUMNS + " FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?) ORDER BY id",
                ps -> {
                    ps.setString(1, value);
                    ps.setString(2, value);
                }, AppService::mapUser);
    }

    public boolean isAdmin(int userId) {
        Boolean admin = Db.one("SELECT COALESCE(is_admin, FALSE) FROM users WHERE id = ?",
                ps -> ps.setInt(1, userId), rs -> rs.getBoolean(1));
        return admin != null && admin;
    }

    public User authenticate(String login, String rawPassword) {
        if (login == null || rawPassword == null || rawPassword.isEmpty()) {
            return null;
        }
        User user = findByLogin(login);
        if (user == null || user.getPassword() == null) {
            return null;
        }
        String stored = user.getPassword();
        if (isHashed(stored)) {
            return passwordEncoder.matches(rawPassword, stored) ? user : null;
        }
        boolean legacyMatch = MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), rawPassword.getBytes(StandardCharsets.UTF_8));
        if (!legacyMatch) {
            return null;
        }
        String hashed = passwordEncoder.encode(rawPassword);
        Db.update("UPDATE users SET password = ? WHERE id = ?", ps -> {
            ps.setString(1, hashed);
            ps.setInt(2, user.getId());
        });
        user.setPassword(hashed);
        return user;
    }

    private static boolean isHashed(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private boolean usernameTaken(String username, int exceptId) {
        return Db.count("SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?) AND id <> ?", ps -> {
            ps.setString(1, username);
            ps.setInt(2, exceptId);
        }) > 0;
    }

    private boolean emailTaken(String email, int exceptId) {
        return Db.count("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?) AND id <> ?", ps -> {
            ps.setString(1, email);
            ps.setInt(2, exceptId);
        }) > 0;
    }

    private String validateIdentity(String username, String email, int exceptId) {
        String error = Validators.validateUsername(username);
        if (error == null) {
            error = Validators.validateEmail(email);
        }
        if (error == null && usernameTaken(username, exceptId)) {
            error = "Имя пользователя уже занято";
        }
        if (error == null && emailTaken(email, exceptId)) {
            error = "Пользователь с таким email уже существует";
        }
        return error;
    }

    public Registered register(String username, String email, String password) {
        String name = Validators.clean(username);
        String mail = Validators.clean(email);
        String error = validateIdentity(name, mail, 0);
        if (error == null) {
            error = Validators.validatePassword(password);
        }
        if (error != null) {
            return new Registered(error, null);
        }
        String hashed = passwordEncoder.encode(password);
        boolean verified = !settings.isEmailVerificationRequired();
        try {
            Db.insert("INSERT INTO users (username, email, password, is_admin, email_verified, created_at, updated_at) VALUES (?, ?, ?, FALSE, ?, NOW(), NOW())",
                    ps -> {
                        ps.setString(1, name);
                        ps.setString(2, mail);
                        ps.setString(3, hashed);
                        ps.setBoolean(4, verified);
                    });
        } catch (RuntimeException e) {
            if (Db.isUniqueViolation(e)) {
                return new Registered("Пользователь с таким email уже существует", null);
            }
            throw e;
        }
        return new Registered(null, findByLogin(mail));
    }

    public ProfileResult updateProfile(int userId, String username, String email, String newPassword) {
        String name = Validators.clean(username);
        String mail = Validators.clean(email);
        String error = validateIdentity(name, mail, userId);
        boolean changePassword = newPassword != null && !newPassword.isEmpty();
        if (error == null && changePassword) {
            error = Validators.validatePassword(newPassword);
        }
        if (error != null) {
            return new ProfileResult(error, false, null);
        }
        User current = getUserById(userId);
        boolean emailChanged = current != null && !current.getEmail().equalsIgnoreCase(mail)
                && settings.isEmailVerificationRequired();
        Db.update("UPDATE users SET username = ?, email = ?, email_verified = CASE WHEN ? THEN FALSE ELSE email_verified END, updated_at = NOW() WHERE id = ?",
                ps -> {
                    ps.setString(1, name);
                    ps.setString(2, mail);
                    ps.setBoolean(3, emailChanged);
                    ps.setInt(4, userId);
                });
        if (changePassword) {
            String hashed = passwordEncoder.encode(newPassword);
            Db.update("UPDATE users SET password = ? WHERE id = ?", ps -> {
                ps.setString(1, hashed);
                ps.setInt(2, userId);
            });
        }
        return new ProfileResult(null, emailChanged, emailChanged ? getUserById(userId) : null);
    }

    public String saveUser(Integer id, String username, String email, String password, boolean admin, int actorId) {
        int userId = id == null ? 0 : id;
        String name = Validators.clean(username);
        String mail = Validators.clean(email);
        String error = validateIdentity(name, mail, userId);
        boolean hasPassword = password != null && !password.isEmpty();
        if (error == null && (userId == 0 || hasPassword)) {
            error = Validators.validatePassword(password);
        }
        if (error == null && userId == actorId && !admin) {
            error = "Нельзя снять права администратора с самого себя";
        }
        if (error != null) {
            return error;
        }
        if (userId == 0) {
            String hashed = passwordEncoder.encode(password);
            Db.insert("INSERT INTO users (username, email, password, is_admin, email_verified, created_at, updated_at) VALUES (?, ?, ?, ?, TRUE, NOW(), NOW())",
                    ps -> {
                        ps.setString(1, name);
                        ps.setString(2, mail);
                        ps.setString(3, hashed);
                        ps.setBoolean(4, admin);
                    });
            return null;
        }
        Db.update("UPDATE users SET username = ?, email = ?, is_admin = ?, updated_at = NOW() WHERE id = ?", ps -> {
            ps.setString(1, name);
            ps.setString(2, mail);
            ps.setBoolean(3, admin);
            ps.setInt(4, userId);
        });
        if (hasPassword) {
            String hashed = passwordEncoder.encode(password);
            Db.update("UPDATE users SET password = ? WHERE id = ?", ps -> {
                ps.setString(1, hashed);
                ps.setInt(2, userId);
            });
        }
        return null;
    }

    public String deleteUser(int id, int actorId) {
        if (id == actorId) {
            return "Нельзя удалить собственную учётную запись";
        }
        Db.update("DELETE FROM users WHERE id = ?", ps -> ps.setInt(1, id));
        return null;
    }

    public long getUsersCount() {
        return Db.count("SELECT COUNT(*) FROM users", Db.NONE);
    }
}
