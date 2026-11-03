package org.example.survey.service;

import org.example.survey.database.Db;
import org.example.survey.model.Question;
import org.example.survey.model.QuestionResult;
import org.example.survey.model.QuestionStats;
import org.example.survey.model.Response;
import org.example.survey.model.Survey;
import org.example.survey.model.SurveyOption;
import org.example.survey.model.SurveyResult;
import org.example.survey.model.SurveyStats;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ResultService {

    public static final int MAX_TEXT_LENGTH = 2000;

    public record SubmitOutcome(boolean ok, String message, int gradedCount, int correctCount, Integer percent)
            implements java.io.Serializable {
        static SubmitOutcome fail(String message) {
            return new SubmitOutcome(false, message, 0, 0, null);
        }

        static SubmitOutcome success(int graded, int correct) {
            Integer percent = graded > 0 ? (int) Math.round(correct * 100.0 / graded) : null;
            return new SubmitOutcome(true, null, graded, correct, percent);
        }
    }

    private static final class AlreadyCompletedException extends RuntimeException {
    }

    private record AnswerRow(int questionId, Integer optionId, String text) {
    }

    private static final String GRADED_SUBQUERY =
            "(SELECT COUNT(*) FROM questions q WHERE q.survey_id = r.survey_id " +
                    "AND q.question_type = 'SINGLE' AND q.correct_option_id IS NOT NULL)";

    private static final String CORRECT_SUBQUERY =
            "(SELECT COUNT(*) FROM answers a JOIN questions q ON q.id = a.question_id " +
                    "WHERE a.response_id = r.id AND q.question_type = 'SINGLE' " +
                    "AND q.correct_option_id IS NOT NULL AND a.option_id = q.correct_option_id)";

    public boolean hasCompleted(int userId, int surveyId) {
        long count = Db.count("SELECT (SELECT COUNT(*) FROM responses WHERE user_id = ? AND survey_id = ?) + " +
                "(SELECT COUNT(*) FROM survey_participants WHERE user_id = ? AND survey_id = ?)", ps -> {
            ps.setInt(1, userId);
            ps.setInt(2, surveyId);
            ps.setInt(3, userId);
            ps.setInt(4, surveyId);
        });
        return count > 0;
    }

    public SubmitOutcome submit(Survey survey, List<Question> questions, Integer userId, Map<String, String[]> params) {
        if (!survey.isOpen()) {
            return SubmitOutcome.fail("Анкета закрыта для прохождения");
        }
        if (questions.isEmpty()) {
            return SubmitOutcome.fail("В анкете нет вопросов");
        }
        if (userId != null && hasCompleted(userId, survey.getId())) {
            return SubmitOutcome.fail("Вы уже проходили эту анкету");
        }
        if (userId == null && !survey.isAnonymous()) {
            return SubmitOutcome.fail("Для прохождения анкеты необходимо войти в систему");
        }

        List<AnswerRow> rows = new ArrayList<>();
        for (Question question : questions) {
            String[] raw = params.get("q_" + question.getId());
            List<String> values = new ArrayList<>();
            if (raw != null) {
                for (String value : raw) {
                    if (value != null && !value.trim().isEmpty()) {
                        values.add(value.trim());
                    }
                }
            }
            if (values.isEmpty()) {
                if (question.isRequired()) {
                    return SubmitOutcome.fail("Ответьте на вопрос: " + shorten(question.getContent()));
                }
                continue;
            }
            if (question.isText()) {
                String text = values.get(0);
                if (text.length() > MAX_TEXT_LENGTH) {
                    return SubmitOutcome.fail("Ответ слишком длинный (максимум " + MAX_TEXT_LENGTH + " символов)");
                }
                rows.add(new AnswerRow(question.getId(), null, text));
                continue;
            }
            if (question.isSingle() && values.size() > 1) {
                return SubmitOutcome.fail("Для вопроса можно выбрать только один вариант: " + shorten(question.getContent()));
            }
            Set<Integer> valid = new HashSet<>();
            for (SurveyOption option : question.getOptions()) {
                valid.add(option.getId());
            }
            Set<Integer> chosen = new HashSet<>();
            for (String value : values) {
                int optionId;
                try {
                    optionId = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return SubmitOutcome.fail("Некорректный ответ на вопрос: " + shorten(question.getContent()));
                }
                if (!valid.contains(optionId)) {
                    return SubmitOutcome.fail("Некорректный ответ на вопрос: " + shorten(question.getContent()));
                }
                if (chosen.add(optionId)) {
                    rows.add(new AnswerRow(question.getId(), optionId, null));
                }
            }
        }

        Integer storedUserId = survey.isAnonymous() ? null : userId;
        try {
            Db.inTransaction(conn -> {
                int responseId = Db.insert(conn,
                        "INSERT INTO responses (user_id, survey_id, submitted_at) VALUES (?, ?, NOW())", ps -> {
                            Db.setNullableInt(ps, 1, storedUserId);
                            ps.setInt(2, survey.getId());
                        });
                for (AnswerRow row : rows) {
                    Db.update(conn,
                            "INSERT INTO answers (response_id, question_id, option_id, text, created_at) VALUES (?, ?, ?, ?, NOW())",
                            ps -> {
                                ps.setInt(1, responseId);
                                ps.setInt(2, row.questionId());
                                Db.setNullableInt(ps, 3, row.optionId());
                                ps.setString(4, row.text());
                            });
                }
                if (survey.isAnonymous() && userId != null) {
                    int inserted = Db.update(conn,
                            "INSERT INTO survey_participants (survey_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING", ps -> {
                                ps.setInt(1, survey.getId());
                                ps.setInt(2, userId);
                            });
                    if (inserted == 0) {
                        throw new AlreadyCompletedException();
                    }
                }
                return responseId;
            });
        } catch (AlreadyCompletedException e) {
            return SubmitOutcome.fail("Вы уже проходили эту анкету");
        } catch (RuntimeException e) {
            if (Db.isUniqueViolation(e)) {
                return SubmitOutcome.fail("Вы уже проходили эту анкету");
            }
            throw e;
        }

        int graded = 0;
        int correct = 0;
        for (Question question : questions) {
            if (!question.isGraded()) {
                continue;
            }
            graded++;
            for (AnswerRow row : rows) {
                if (row.questionId() == question.getId() && question.getCorrectOptionId().equals(row.optionId())) {
                    correct++;
                    break;
                }
            }
        }
        return SubmitOutcome.success(graded, correct);
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 80 ? text.substring(0, 77) + "..." : text;
    }

    private static SurveyResult mapResult(ResultSet rs) throws SQLException {
        SurveyResult result = new SurveyResult();
        result.setSurveyId(rs.getInt("survey_id"));
        result.setSurveyTitle(rs.getString("title"));
        result.setSurveyDescription(rs.getString("description"));
        result.setSubmittedAt(Db.time(rs, "submitted_at"));
        int graded = rs.getInt("graded");
        int correct = rs.getInt("correct");
        result.setGradedCount(graded);
        result.setCorrectCount(correct);
        result.setPercent(graded > 0 ? (int) Math.round(correct * 100.0 / graded) : null);
        return result;
    }

    private static final String RESULT_SELECT =
            "SELECT r.survey_id, s.title, s.description, r.submitted_at, " +
                    GRADED_SUBQUERY + " AS graded, " + CORRECT_SUBQUERY + " AS correct " +
                    "FROM responses r JOIN surveys s ON s.id = r.survey_id WHERE r.user_id = ?";

    public SurveyResult getResult(int userId, int surveyId) {
        return Db.one(RESULT_SELECT + " AND r.survey_id = ?", ps -> {
            ps.setInt(1, userId);
            ps.setInt(2, surveyId);
        }, ResultService::mapResult);
    }

    public List<SurveyResult> getUserResults(int userId) {
        return Db.list(RESULT_SELECT + " ORDER BY r.submitted_at DESC, r.id DESC",
                ps -> ps.setInt(1, userId), ResultService::mapResult);
    }

    public Integer getAveragePercent(List<SurveyResult> results) {
        int sum = 0;
        int count = 0;
        for (SurveyResult result : results) {
            if (result.getPercent() != null) {
                sum += result.getPercent();
                count++;
            }
        }
        return count == 0 ? null : (int) Math.round(sum / (double) count);
    }

    public List<QuestionResult> getQuestionResults(int userId, int surveyId, List<Question> questions) {
        record Row(int questionId, Integer optionId, String text, String optionText) {
        }
        List<Row> rows = Db.list(
                "SELECT a.question_id, a.option_id, a.text, o.content AS option_text FROM answers a " +
                        "JOIN responses r ON r.id = a.response_id LEFT JOIN options o ON o.id = a.option_id " +
                        "WHERE r.user_id = ? AND r.survey_id = ? ORDER BY a.id",
                ps -> {
                    ps.setInt(1, userId);
                    ps.setInt(2, surveyId);
                },
                rs -> new Row(rs.getInt("question_id"), Db.nullableInt(rs, "option_id"),
                        rs.getString("text"), rs.getString("option_text")));

        Map<Integer, List<Row>> byQuestion = new HashMap<>();
        for (Row row : rows) {
            byQuestion.computeIfAbsent(row.questionId(), key -> new ArrayList<>()).add(row);
        }

        List<QuestionResult> results = new ArrayList<>();
        for (Question question : questions) {
            QuestionResult result = new QuestionResult();
            result.setQuestion(question);
            List<Row> given = byQuestion.getOrDefault(question.getId(), List.of());
            for (Row row : given) {
                if (row.optionText() != null) {
                    result.getChosen().add(row.optionText());
                } else if (row.text() != null) {
                    result.setText(row.text());
                }
            }
            if (question.isGraded()) {
                boolean correct = given.stream().anyMatch(row ->
                        row.optionId() != null && row.optionId().equals(question.getCorrectOptionId()));
                result.setCorrect(correct);
                for (SurveyOption option : question.getOptions()) {
                    if (option.getId() == question.getCorrectOptionId()) {
                        result.setCorrectText(option.getContent());
                    }
                }
            }
            results.add(result);
        }
        return results;
    }

    public SurveyStats getStats(Survey survey, List<Question> questions) {
        int surveyId = survey.getId();
        SurveyStats stats = new SurveyStats();
        stats.setSurvey(survey);
        stats.setResponsesCount(survey.getResponsesCount());

        Map<Integer, Integer> answeredByQuestion = new HashMap<>();
        Db.list("SELECT a.question_id, COUNT(DISTINCT a.response_id) AS cnt FROM answers a " +
                        "JOIN responses r ON r.id = a.response_id WHERE r.survey_id = ? GROUP BY a.question_id",
                ps -> ps.setInt(1, surveyId),
                rs -> answeredByQuestion.put(rs.getInt("question_id"), rs.getInt("cnt")));

        Map<Integer, Integer> optionCounts = new HashMap<>();
        Db.list("SELECT a.option_id, COUNT(*) AS cnt FROM answers a JOIN responses r ON r.id = a.response_id " +
                        "WHERE r.survey_id = ? AND a.option_id IS NOT NULL GROUP BY a.option_id",
                ps -> ps.setInt(1, surveyId),
                rs -> optionCounts.put(rs.getInt("option_id"), rs.getInt("cnt")));

        Map<Integer, List<String>> texts = new HashMap<>();
        Db.list("SELECT a.question_id, a.text FROM answers a JOIN responses r ON r.id = a.response_id " +
                        "WHERE r.survey_id = ? AND a.text IS NOT NULL AND a.text <> '' ORDER BY a.id DESC LIMIT 500",
                ps -> ps.setInt(1, surveyId),
                rs -> texts.computeIfAbsent(rs.getInt("question_id"), key -> new ArrayList<>()).add(rs.getString("text")));

        for (Question question : questions) {
            QuestionStats questionStats = new QuestionStats();
            questionStats.setQuestion(question);
            int answered = answeredByQuestion.getOrDefault(question.getId(), 0);
            questionStats.setAnswered(answered);
            for (SurveyOption option : question.getOptions()) {
                int count = optionCounts.getOrDefault(option.getId(), 0);
                option.setAnswersCount(count);
                option.setPercent(answered == 0 ? 0 : (int) Math.round(count * 100.0 / answered));
            }
            questionStats.setTexts(texts.getOrDefault(question.getId(), new ArrayList<>()));
            stats.getQuestions().add(questionStats);
        }

        Integer average = Db.one(
                "SELECT AVG(t.correct * 100.0 / t.graded) FROM (SELECT " + CORRECT_SUBQUERY + " AS correct, " +
                        GRADED_SUBQUERY + " AS graded FROM responses r WHERE r.survey_id = ?) t WHERE t.graded > 0",
                ps -> ps.setInt(1, surveyId),
                rs -> {
                    double value = rs.getDouble(1);
                    return rs.wasNull() ? null : (int) Math.round(value);
                });
        stats.setAveragePercent(average);
        return stats;
    }

    public List<Response> getAllResponses(int limit) {
        return Db.list(
                "SELECT r.id, r.user_id, u.username, r.survey_id, s.title, r.submitted_at FROM responses r " +
                        "LEFT JOIN users u ON u.id = r.user_id JOIN surveys s ON s.id = r.survey_id " +
                        "ORDER BY r.submitted_at DESC, r.id DESC LIMIT " + limit,
                Db.NONE, rs -> {
                    Response response = new Response();
                    response.setId(rs.getInt("id"));
                    response.setUserId(rs.getInt("user_id"));
                    String author = rs.getString("username");
                    response.setUsername(author == null ? "Аноним" : author);
                    response.setSurveyId(rs.getInt("survey_id"));
                    response.setSurveyTitle(rs.getString("title"));
                    response.setSubmittedAt(Db.time(rs, "submitted_at"));
                    return response;
                });
    }

    public void deleteResponse(int id) {
        Db.update("DELETE FROM responses WHERE id = ?", ps -> ps.setInt(1, id));
    }
}
