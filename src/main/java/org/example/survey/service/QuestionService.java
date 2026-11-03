package org.example.survey.service;

import org.example.survey.database.Db;
import org.example.survey.model.Question;
import org.example.survey.model.SurveyOption;
import org.example.survey.util.Validators;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuestionService {

    public static final int MAX_OPTIONS = 20;
    public static final Set<String> TYPES = Set.of("SINGLE", "MULTIPLE", "TEXT");

    private static final String QUESTION_COLUMNS =
            "id, content, survey_id, question_type, is_required, correct_option_id, created_at, updated_at";

    private static Question mapQuestion(ResultSet rs) throws SQLException {
        Question question = new Question();
        question.setId(rs.getInt("id"));
        question.setContent(rs.getString("content"));
        question.setSurveyId(rs.getInt("survey_id"));
        question.setType(rs.getString("question_type"));
        question.setRequired(rs.getBoolean("is_required"));
        question.setCorrectOptionId(Db.nullableInt(rs, "correct_option_id"));
        question.setCreatedAt(Db.time(rs, "created_at"));
        question.setUpdatedAt(Db.time(rs, "updated_at"));
        return question;
    }

    private static SurveyOption mapOption(ResultSet rs) throws SQLException {
        SurveyOption option = new SurveyOption();
        option.setId(rs.getInt("id"));
        option.setContent(rs.getString("content"));
        option.setQuestionId(rs.getInt("question_id"));
        option.setCreatedAt(Db.time(rs, "created_at"));
        option.setUpdatedAt(Db.time(rs, "updated_at"));
        return option;
    }

    public List<Question> getQuestions(int surveyId) {
        List<Question> questions = Db.list(
                "SELECT " + QUESTION_COLUMNS + " FROM questions WHERE survey_id = ? ORDER BY id",
                ps -> ps.setInt(1, surveyId), QuestionService::mapQuestion);
        if (questions.isEmpty()) {
            return questions;
        }
        List<SurveyOption> options = Db.list(
                "SELECT o.id, o.content, o.question_id, o.created_at, o.updated_at FROM options o " +
                        "JOIN questions q ON q.id = o.question_id WHERE q.survey_id = ? ORDER BY o.id",
                ps -> ps.setInt(1, surveyId), QuestionService::mapOption);
        Map<Integer, Question> byId = new HashMap<>();
        for (Question question : questions) {
            byId.put(question.getId(), question);
        }
        for (SurveyOption option : options) {
            Question owner = byId.get(option.getQuestionId());
            if (owner != null) {
                owner.getOptions().add(option);
            }
        }
        return questions;
    }

    public Question getQuestion(int id) {
        return Db.one("SELECT " + QUESTION_COLUMNS + " FROM questions WHERE id = ?",
                ps -> ps.setInt(1, id), QuestionService::mapQuestion);
    }

    public SurveyOption getOption(int id) {
        return Db.one("SELECT id, content, question_id, created_at, updated_at FROM options WHERE id = ?",
                ps -> ps.setInt(1, id), QuestionService::mapOption);
    }

    public record SavedQuestion(int id, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    public SavedQuestion createQuestion(int surveyId, String content, String type, boolean required) {
        String error = validateQuestion(content, type);
        if (error != null) {
            return new SavedQuestion(0, error);
        }
        String text = Validators.clean(content);
        int id = Db.insert("INSERT INTO questions (content, survey_id, question_type, is_required, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                ps -> {
                    ps.setString(1, text);
                    ps.setInt(2, surveyId);
                    ps.setString(3, type);
                    ps.setBoolean(4, required);
                });
        return new SavedQuestion(id, null);
    }

    private String validateQuestion(String content, String type) {
        String text = Validators.clean(content);
        if (text.isEmpty()) {
            return "Введите текст вопроса";
        }
        if (text.length() > 1000) {
            return "Текст вопроса не должен превышать 1000 символов";
        }
        if (type == null || !TYPES.contains(type)) {
            return "Неизвестный тип вопроса";
        }
        return null;
    }

    public String saveQuestion(Integer id, int surveyId, String content, String type, boolean required) {
        String error = validateQuestion(content, type);
        if (error != null) {
            return error;
        }
        String text = Validators.clean(content);
        if (id == null) {
            return createQuestion(surveyId, text, type, required).error();
        }
        Db.update("UPDATE questions SET content = ?, question_type = ?, is_required = ?, " +
                        "correct_option_id = CASE WHEN ? = 'SINGLE' THEN correct_option_id ELSE NULL END, updated_at = NOW() " +
                        "WHERE id = ? AND survey_id = ?",
                ps -> {
                    ps.setString(1, text);
                    ps.setString(2, type);
                    ps.setBoolean(3, required);
                    ps.setString(4, type);
                    ps.setInt(5, id);
                    ps.setInt(6, surveyId);
                });
        return null;
    }

    public void deleteQuestion(int id) {
        Db.update("DELETE FROM questions WHERE id = ?", ps -> ps.setInt(1, id));
    }

    public String addOption(int questionId, String content) {
        Question question = getQuestion(questionId);
        if (question == null) {
            return "Вопрос не найден";
        }
        if (!question.isChoice()) {
            return "Для текстового вопроса варианты не нужны";
        }
        String text = Validators.clean(content);
        if (text.isEmpty()) {
            return "Введите текст варианта";
        }
        if (text.length() > 500) {
            return "Вариант не должен превышать 500 символов";
        }
        long count = Db.count("SELECT COUNT(*) FROM options WHERE question_id = ?", ps -> ps.setInt(1, questionId));
        if (count >= MAX_OPTIONS) {
            return "Максимум вариантов в вопросе: " + MAX_OPTIONS;
        }
        Db.insert("INSERT INTO options (content, question_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())", ps -> {
            ps.setString(1, text);
            ps.setInt(2, questionId);
        });
        return null;
    }

    public void deleteOption(int optionId) {
        Db.update("UPDATE questions SET correct_option_id = NULL WHERE correct_option_id = ?", ps -> ps.setInt(1, optionId));
        Db.update("DELETE FROM options WHERE id = ?", ps -> ps.setInt(1, optionId));
    }

    public String setCorrectOption(int questionId, Integer optionId) {
        Question question = getQuestion(questionId);
        if (question == null) {
            return "Вопрос не найден";
        }
        if (!question.isSingle()) {
            return "Правильный ответ можно задать только для вопроса с одним вариантом";
        }
        if (optionId != null) {
            SurveyOption option = getOption(optionId);
            if (option == null || option.getQuestionId() != questionId) {
                return "Вариант не принадлежит вопросу";
            }
        }
        Db.update("UPDATE questions SET correct_option_id = ?, updated_at = NOW() WHERE id = ?", ps -> {
            Db.setNullableInt(ps, 1, optionId);
            ps.setInt(2, questionId);
        });
        return null;
    }

}
