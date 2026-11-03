package org.example.survey.service;

import org.example.survey.database.Db;
import org.example.survey.model.Page;
import org.example.survey.model.Survey;
import org.example.survey.util.TokenUtil;
import org.example.survey.util.Validators;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class SurveyService {

    public static final int PAGE_SIZE = 9;

    public record Saved(int id, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private static final String ANONYMOUS_ACCESS_ERROR = "Анонимная анкета должна быть открыта для всех";

    private record Resolved(List<Integer> ids, List<String> unknown) {
    }

    private static final String BASE_SELECT =
            "SELECT s.id, s.title, s.description, s.created_by, s.created_at, s.updated_at, s.access_users, " +
                    "s.is_active, s.ends_at, s.is_anonymous, s.public_token, u.username AS creator_name, " +
                    "(SELECT COUNT(*) FROM questions q WHERE q.survey_id = s.id) AS questions_count, " +
                    "(SELECT COUNT(*) FROM responses r WHERE r.survey_id = s.id) AS responses_count";

    private static final String BASE_FROM = " FROM surveys s LEFT JOIN users u ON u.id = s.created_by ";

    private static Survey mapSurvey(ResultSet rs, boolean withCompleted) throws SQLException {
        Survey survey = new Survey();
        survey.setId(rs.getInt("id"));
        survey.setTitle(rs.getString("title"));
        survey.setDescription(rs.getString("description"));
        survey.setCreatedBy(rs.getInt("created_by"));
        survey.setCreatorName(rs.getString("creator_name"));
        survey.setCreatedAt(Db.time(rs, "created_at"));
        survey.setUpdatedAt(Db.time(rs, "updated_at"));
        String access = rs.getString("access_users");
        survey.setAccessUsers(access == null ? "*" : access);
        survey.setActive(rs.getBoolean("is_active"));
        survey.setEndsAt(Db.time(rs, "ends_at"));
        survey.setAnonymous(rs.getBoolean("is_anonymous"));
        survey.setPublicToken(rs.getString("public_token"));
        survey.setQuestionsCount(rs.getInt("questions_count"));
        survey.setResponsesCount(rs.getInt("responses_count"));
        if (withCompleted) {
            survey.setCompleted(rs.getBoolean("completed"));
        }
        return survey;
    }

    public Survey getSurveyById(int id) {
        Survey survey = Db.one(BASE_SELECT + BASE_FROM + "WHERE s.id = ?",
                ps -> ps.setInt(1, id), rs -> mapSurvey(rs, false));
        if (survey != null && !survey.isPublic()) {
            survey.setAccessList(String.join(", ", getAccessUsernames(id)));
        }
        return survey;
    }

    public Survey getSurveyByToken(String token) {
        if (token == null || token.isBlank() || token.length() > 64) {
            return null;
        }
        return Db.one(BASE_SELECT + BASE_FROM + "WHERE s.public_token = ? AND s.is_anonymous = TRUE",
                ps -> ps.setString(1, token), rs -> mapSurvey(rs, false));
    }

    public List<Survey> getAllSurveys() {
        return Db.list(BASE_SELECT + BASE_FROM + "ORDER BY s.created_at DESC, s.id DESC",
                Db.NONE, rs -> mapSurvey(rs, false));
    }

    public List<Survey> getSurveysByCreator(int userId) {
        return Db.list(BASE_SELECT + BASE_FROM + "WHERE s.created_by = ? ORDER BY s.created_at DESC, s.id DESC",
                ps -> ps.setInt(1, userId), rs -> mapSurvey(rs, false));
    }

    public Page listAvailable(int userId, String query, int requestedPage, int size) {
        String search = Validators.clean(query);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(userId);
        StringBuilder where = new StringBuilder(
                " WHERE s.is_active = TRUE AND (s.ends_at IS NULL OR s.ends_at > NOW())" +
                        " AND EXISTS (SELECT 1 FROM questions qq WHERE qq.survey_id = s.id)" +
                        " AND (s.access_users = '*' OR s.created_by = ?" +
                        " OR EXISTS (SELECT 1 FROM survey_access sa WHERE sa.survey_id = s.id AND sa.user_id = ?))");
        if (!search.isEmpty()) {
            String like = "%" + escapeLike(search.toLowerCase()) + "%";
            where.append(" AND (LOWER(s.title) LIKE ? OR LOWER(COALESCE(s.description, '')) LIKE ?)");
            params.add(like);
            params.add(like);
        }
        long total = Db.count("SELECT COUNT(*) FROM surveys s" + where, ps -> bind(ps, params, 1));
        int totalPages = (int) Math.max(1, (total + size - 1) / size);
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        int offset = (page - 1) * size;

        List<Object> listParams = new ArrayList<>();
        listParams.add(userId);
        listParams.add(userId);
        listParams.addAll(params);
        String sql = BASE_SELECT +
                ", (EXISTS (SELECT 1 FROM responses rc WHERE rc.survey_id = s.id AND rc.user_id = ?) " +
                "OR EXISTS (SELECT 1 FROM survey_participants sp WHERE sp.survey_id = s.id AND sp.user_id = ?)) AS completed" +
                BASE_FROM + where + " ORDER BY s.created_at DESC, s.id DESC LIMIT " + size + " OFFSET " + offset;
        List<Survey> items = Db.list(sql, ps -> bind(ps, listParams, 1), rs -> mapSurvey(rs, true));

        Page result = new Page();
        result.setItems(items);
        result.setPage(page);
        result.setTotalPages(totalPages);
        result.setTotal(total);
        result.setQuery(search);
        return result;
    }

    private static void bind(java.sql.PreparedStatement ps, List<Object> params, int start) throws SQLException {
        int index = start;
        for (Object param : params) {
            ps.setObject(index++, param);
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public boolean canAccess(Survey survey, int userId, boolean admin) {
        if (survey == null) {
            return false;
        }
        if (admin || survey.getCreatedBy() == userId || survey.isPublic()) {
            return true;
        }
        return Db.count("SELECT COUNT(*) FROM survey_access WHERE survey_id = ? AND user_id = ?", ps -> {
            ps.setInt(1, survey.getId());
            ps.setInt(2, userId);
        }) > 0;
    }

    public boolean canManage(Survey survey, int userId, boolean admin) {
        return survey != null && (admin || survey.getCreatedBy() == userId);
    }

    public List<String> getAccessUsernames(int surveyId) {
        return Db.list("SELECT u.username FROM survey_access sa JOIN users u ON u.id = sa.user_id WHERE sa.survey_id = ? ORDER BY u.username",
                ps -> ps.setInt(1, surveyId), rs -> rs.getString(1));
    }

    private Resolved resolveUsers(String raw) {
        Set<String> tokens = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split("[,;\\n]")) {
                String token = part.trim();
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
        }
        List<Integer> ids = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String token : tokens) {
            Integer id = Db.one("SELECT id FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?) ORDER BY id LIMIT 1",
                    ps -> {
                        ps.setString(1, token);
                        ps.setString(2, token);
                    }, rs -> rs.getInt(1));
            if (id == null) {
                unknown.add(token);
            } else if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return new Resolved(ids, unknown);
    }

    private String validateMeta(String title, String description) {
        if (title.isEmpty()) {
            return "Укажите название анкеты";
        }
        if (title.length() > 100) {
            return "Название не должно превышать 100 символов";
        }
        if (description.length() > 2000) {
            return "Описание не должно превышать 2000 символов";
        }
        return null;
    }

    public Saved createSurvey(int ownerId, String title, String description, boolean publicAccess,
                              String accessList, LocalDateTime endsAt, boolean anonymous) {
        String name = Validators.clean(title);
        String text = Validators.clean(description);
        String error = validateMeta(name, text);
        if (error == null && anonymous && !publicAccess) {
            error = ANONYMOUS_ACCESS_ERROR;
        }
        Resolved resolved = new Resolved(List.of(), List.of());
        if (error == null && !publicAccess) {
            resolved = resolveUsers(accessList);
            error = accessError(resolved);
        }
        if (error != null) {
            return new Saved(0, error);
        }
        final List<Integer> accessIds = resolved.ids();
        int id = Db.inTransaction(conn -> {
            int newId = Db.insert(conn,
                    "INSERT INTO surveys (title, description, created_by, access_users, is_active, ends_at, is_anonymous, public_token, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, FALSE, ?, ?, ?, NOW(), NOW())",
                    ps -> {
                        ps.setString(1, name);
                        ps.setString(2, text);
                        ps.setInt(3, ownerId);
                        ps.setString(4, publicAccess ? "*" : "LIST");
                        ps.setTimestamp(5, Db.timestamp(endsAt));
                        ps.setBoolean(6, anonymous);
                        ps.setString(7, anonymous ? TokenUtil.generate(18) : null);
                    });
            replaceAccess(conn, newId, accessIds);
            return newId;
        });
        return new Saved(id, null);
    }

    public String updateSurvey(int surveyId, String title, String description, boolean publicAccess,
                               String accessList, LocalDateTime endsAt, boolean anonymous) {
        String name = Validators.clean(title);
        String text = Validators.clean(description);
        String error = validateMeta(name, text);
        Survey current = getSurveyById(surveyId);
        if (error == null && current == null) {
            error = "Анкета не найдена";
        }
        if (error == null && anonymous && !publicAccess) {
            error = ANONYMOUS_ACCESS_ERROR;
        }
        if (error == null && current.isAnonymous() != anonymous && current.getResponsesCount() > 0) {
            error = "Нельзя менять анонимность анкеты, у которой уже есть ответы";
        }
        Resolved resolved = new Resolved(List.of(), List.of());
        if (error == null && !publicAccess) {
            resolved = resolveUsers(accessList);
            error = accessError(resolved);
        }
        if (error != null) {
            return error;
        }
        final List<Integer> accessIds = resolved.ids();
        Db.inTransaction(conn -> {
            Db.update(conn, "UPDATE surveys SET title = ?, description = ?, access_users = ?, ends_at = ?, is_anonymous = ?, " +
                            "public_token = CASE WHEN ? THEN COALESCE(public_token, ?) ELSE public_token END, updated_at = NOW() WHERE id = ?",
                    ps -> {
                        ps.setString(1, name);
                        ps.setString(2, text);
                        ps.setString(3, publicAccess ? "*" : "LIST");
                        ps.setTimestamp(4, Db.timestamp(endsAt));
                        ps.setBoolean(5, anonymous);
                        ps.setBoolean(6, anonymous);
                        ps.setString(7, TokenUtil.generate(18));
                        ps.setInt(8, surveyId);
                    });
            replaceAccess(conn, surveyId, accessIds);
            return null;
        });
        return null;
    }

    private String accessError(Resolved resolved) {
        if (!resolved.unknown().isEmpty()) {
            return "Пользователи не найдены: " + String.join(", ", resolved.unknown());
        }
        if (resolved.ids().isEmpty()) {
            return "Укажите хотя бы одного пользователя или откройте доступ всем";
        }
        return null;
    }

    private void replaceAccess(java.sql.Connection conn, int surveyId, List<Integer> userIds) throws SQLException {
        Db.update(conn, "DELETE FROM survey_access WHERE survey_id = ?", ps -> ps.setInt(1, surveyId));
        for (int userId : userIds) {
            Db.update(conn, "INSERT INTO survey_access (survey_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING", ps -> {
                ps.setInt(1, surveyId);
                ps.setInt(2, userId);
            });
        }
    }

    public String setActive(int surveyId, boolean active) {
        if (active) {
            long questions = Db.count("SELECT COUNT(*) FROM questions WHERE survey_id = ?", ps -> ps.setInt(1, surveyId));
            if (questions == 0) {
                return "Добавьте хотя бы один вопрос перед публикацией";
            }
        }
        Db.update("UPDATE surveys SET is_active = ?, updated_at = NOW() WHERE id = ?", ps -> {
            ps.setBoolean(1, active);
            ps.setInt(2, surveyId);
        });
        return null;
    }

    public void deleteSurvey(int id) {
        Db.update("DELETE FROM surveys WHERE id = ?", ps -> ps.setInt(1, id));
    }

    public long getTotalSurveysCount() {
        return Db.count("SELECT COUNT(*) FROM surveys", Db.NONE);
    }

    public long getTotalResponsesCount() {
        return Db.count("SELECT COUNT(*) FROM responses", Db.NONE);
    }

    public long getActiveUsersCount() {
        return Db.count("SELECT COUNT(DISTINCT user_id) FROM responses WHERE submitted_at >= NOW() - INTERVAL '30 days'", Db.NONE);
    }
}
