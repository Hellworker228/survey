package org.example.survey.api;

import java.util.List;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record LoginRequest(String login, String password) {
    }

    public record RegisterRequest(String username, String email, String password) {
    }

    public record ResendRequest(String login) {
    }

    public record UserDto(int id, String username, String email, boolean admin, boolean emailVerified) {
    }

    public record TokenResponse(String token, String tokenType, String expiresAt, UserDto user) {
    }

    public record RegisterResponse(String message, boolean emailVerificationRequired, boolean emailSent, UserDto user) {
    }

    public record MessageResponse(String message) {
    }

    public record OptionDto(int id, String content) {
    }

    public record QuestionDto(int id, String content, String type, boolean required,
                              List<OptionDto> options, Integer correctOptionId) {
    }

    public record SurveyDto(int id, String title, String description, String creator, boolean active, boolean open,
                            boolean anonymous, boolean publicAccess, String endsAt, int questionsCount,
                            int responsesCount, boolean completed, String publicToken, String publicPath,
                            List<QuestionDto> questions) {
    }

    public record PageDto<T>(List<T> items, int page, int totalPages, long total) {
    }

    public record SurveyRequest(String title, String description, Boolean publicAccess, String accessList,
                                String endsAt, Boolean anonymous) {
    }

    public record QuestionRequest(String content, String type, Boolean required, List<String> options,
                                  Integer correctIndex) {
    }

    public record OptionRequest(String content) {
    }

    public record CorrectRequest(Integer optionId) {
    }

    public record AnswerRequest(Integer questionId, List<Integer> optionIds, String text) {
    }

    public record SubmitRequest(List<AnswerRequest> answers) {
    }

    public record SubmitResponse(boolean anonymous, Integer percent, int correct, int graded) {
    }

    public record QuestionResultDto(int questionId, String content, List<String> chosen, String text,
                                    Boolean correct, String correctAnswer) {
    }

    public record ResultDto(int surveyId, String surveyTitle, String submittedAt, Integer percent,
                            int correct, int graded, List<QuestionResultDto> questions) {
    }

    public record ResultSummaryDto(int surveyId, String surveyTitle, String submittedAt, Integer percent,
                                   int correct, int graded) {
    }

    public record OptionStatDto(int id, String content, int count, int percent, boolean correct) {
    }

    public record QuestionStatDto(int questionId, String content, String type, int answered,
                                  List<OptionStatDto> options, List<String> texts) {
    }

    public record StatsDto(int surveyId, String title, int responses, Integer averagePercent,
                           List<QuestionStatDto> questions) {
    }
}
