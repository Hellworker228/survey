package org.example.survey.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.survey.database.Db;
import org.example.survey.model.Question;
import org.example.survey.model.Survey;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private record ResponseRow(int id, String username, String email, LocalDateTime submittedAt) {
    }

    private record AnswerRow(int responseId, int questionId, Integer optionId, String optionText, String text) {
    }

    private final QuestionService questionService;

    public ExportService(QuestionService questionService) {
        this.questionService = questionService;
    }

    public byte[] exportSurvey(Survey survey) throws IOException {
        List<Question> questions = questionService.getQuestions(survey.getId());
        List<ResponseRow> responses = Db.list(
                "SELECT r.id, u.username, u.email, r.submitted_at FROM responses r LEFT JOIN users u ON u.id = r.user_id " +
                        "WHERE r.survey_id = ? ORDER BY r.submitted_at, r.id",
                ps -> ps.setInt(1, survey.getId()),
                rs -> new ResponseRow(rs.getInt("id"), rs.getString("username"), rs.getString("email"),
                        Db.time(rs, "submitted_at")));
        boolean anonymous = survey.isAnonymous();
        List<AnswerRow> answers = Db.list(
                "SELECT a.response_id, a.question_id, a.option_id, o.content AS option_text, a.text FROM answers a " +
                        "JOIN responses r ON r.id = a.response_id LEFT JOIN options o ON o.id = a.option_id " +
                        "WHERE r.survey_id = ? ORDER BY a.id",
                ps -> ps.setInt(1, survey.getId()),
                rs -> new AnswerRow(rs.getInt("response_id"), rs.getInt("question_id"),
                        Db.nullableInt(rs, "option_id"), rs.getString("option_text"), rs.getString("text")));

        Map<Integer, Map<Integer, List<AnswerRow>>> grouped = new HashMap<>();
        for (AnswerRow answer : answers) {
            grouped.computeIfAbsent(answer.responseId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(answer.questionId(), key -> new ArrayList<>())
                    .add(answer);
        }

        boolean graded = questions.stream().anyMatch(Question::isGraded);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Ответы");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            List<String> titles = new ArrayList<>(anonymous
                    ? List.of("Участник", "Дата отправки")
                    : List.of("Пользователь", "Email", "Дата отправки"));
            for (Question question : questions) {
                titles.add(question.getContent());
            }
            if (graded) {
                titles.add("Результат, %");
            }
            for (int i = 0; i < titles.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(safe(titles.get(i)));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (ResponseRow response : responses) {
                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                if (anonymous) {
                    row.createCell(col++).setCellValue("Аноним " + (rowIndex - 1));
                } else {
                    row.createCell(col++).setCellValue(safe(response.username() == null ? "Аноним" : response.username()));
                    row.createCell(col++).setCellValue(safe(response.email()));
                }
                row.createCell(col++).setCellValue(response.submittedAt() == null ? "" : response.submittedAt().format(DATE_TIME));
                Map<Integer, List<AnswerRow>> byQuestion = grouped.getOrDefault(response.id(), Map.of());
                int gradedCount = 0;
                int correctCount = 0;
                for (Question question : questions) {
                    List<AnswerRow> given = byQuestion.getOrDefault(question.getId(), List.of());
                    List<String> values = new ArrayList<>();
                    for (AnswerRow answer : given) {
                        values.add(answer.optionText() != null ? answer.optionText() : answer.text());
                    }
                    row.createCell(col++).setCellValue(safe(String.join("; ", values)));
                    if (question.isGraded()) {
                        gradedCount++;
                        boolean correct = given.stream().anyMatch(answer ->
                                answer.optionId() != null && answer.optionId().equals(question.getCorrectOptionId()));
                        if (correct) {
                            correctCount++;
                        }
                    }
                }
                if (graded) {
                    Cell cell = row.createCell(col);
                    if (gradedCount > 0) {
                        cell.setCellValue(Math.round(correctCount * 100.0 / gradedCount));
                    }
                }
            }
            for (int i = 0; i < titles.size(); i++) {
                sheet.setColumnWidth(i, Math.min(60, Math.max(16, titles.get(i).length() + 4)) * 256);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    static String safe(String value) {
        if (value == null) {
            return "";
        }
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
