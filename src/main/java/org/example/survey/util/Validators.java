package org.example.survey.util;

import java.util.regex.Pattern;

public final class Validators {

    private static final Pattern USERNAME = Pattern.compile("^[\\p{L}\\p{N}_.\\-]{3,50}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    public static final int MIN_PASSWORD = 6;
    public static final int MAX_PASSWORD = 72;

    private Validators() {
    }

    public static String validateUsername(String username) {
        if (username == null || !USERNAME.matcher(username.trim()).matches()) {
            return "Имя пользователя: 3–50 символов, буквы, цифры, точка, дефис и подчёркивание";
        }
        return null;
    }

    public static String validateEmail(String email) {
        if (email == null || email.trim().length() > 100 || !EMAIL.matcher(email.trim()).matches()) {
            return "Введите корректный email";
        }
        return null;
    }

    public static String validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            return "Пароль должен содержать не менее " + MIN_PASSWORD + " символов";
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD) {
            return "Пароль слишком длинный";
        }
        return null;
    }

    public static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
