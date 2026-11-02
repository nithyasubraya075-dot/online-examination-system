package com.smartexam.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class Api {
    private Api() {}
    public record RegisterRequest(@NotBlank @Size(max = 120) String fullName,
                                  @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{4,24}") String usn,
                                  @Email @NotBlank @Size(max = 254) String email,
                                  @NotBlank @Pattern(regexp = "\\+?[0-9 ()-]{7,20}") String phone,
                                  @NotNull Long branchId, @Min(1) @Max(8) int semester,
                                  @NotBlank @Size(min = 8, max = 72) String password, @NotBlank String confirmPassword) {}
    public record UserView(Long id, String email, String role, String fullName, String usn, String branch, Integer semester) {}
    public record BranchView(Long id, String code, String name) {}
    public record SemesterView(Long id, int number, long subjectCount, long examCount,
                               long upcomingExams, long availableExams, long completedExams) {}
    public record SubjectRequest(@NotBlank String code, @NotBlank String name, @NotNull Long branchId,
                                 @Min(1) @Max(8) int semester, String description, Long teacherId,
                                 @Min(1) int totalMarks, @Min(1) int passingMarks, @Min(1) int durationMinutes,
                                 boolean available) {}
    public record SubjectView(Long id, String code, String name, String branch, String branchCode, int semester,
                              String description, String teacher, Long teacherId, int totalMarks, int passingMarks,
                              int durationMinutes, boolean available, long examCount) {}
    public record ExamRequest(@NotBlank String title, @NotNull Long subjectId, @NotNull Instant startTime,
                              @NotNull Instant endTime, @Min(1) int durationMinutes, @Min(1) int totalMarks,
                              @Min(1) int passingMarks, boolean allowMultipleAttempts) {}
    public record MultipleAttemptsRequest(boolean enabled) {}
    public record ExamView(Long id, String title, String subject, String subjectCode, String branch,
                           int semester, Instant startTime, Instant endTime, int durationMinutes,
                           int totalMarks, int passingMarks, String status, long questionCount,
                           boolean allowMultipleAttempts, boolean attempted, Long attemptId) {}
    public record QuestionRequest(@NotBlank String prompt, @NotBlank String optionA, @NotBlank String optionB,
                                  @NotBlank String optionC, @NotBlank String optionD,
                                  @NotBlank @Pattern(regexp = "A|B|C|D") String correctOption, @Min(1) int marks) {}
    public record QuestionView(Long id, String prompt, String optionA, String optionB, String optionC,
                               String optionD, Integer marks, String correctOption) {}
    public record AnswerRequest(@NotNull Long questionId, @Pattern(regexp = "A|B|C|D") String selectedOption, boolean markedForReview) {}
    public record AttemptView(Long id, Long examId, String title, String subject, Instant startedAt,
                              Instant deadline, Instant serverNow, List<QuestionView> questions, List<AnswerView> answers) {}
    public record AnswerView(Long questionId, String selectedOption, boolean markedForReview) {}
    public record ResultView(Long id, Long attemptId, String exam, String subject, String subjectCode,
                             String branch, int semester, String student, String usn, Instant submittedAt,
                             int correct, int wrong, int unanswered, double obtainedMarks, int totalMarks,
                             double percentage, boolean passed) {}
    public record DashboardView(UserView user, long subjectCount, long availableExams, long upcomingExams,
                                long completedExams, double averagePercentage, long passed, long failed,
                                List<ResultView> recentResults) {}
    public record TeacherRequest(@NotBlank @Size(max = 120) String fullName, @Email @NotBlank @Size(max = 254) String email,
                                 @NotBlank @Pattern(regexp = "\\+?[0-9 ()-]{7,20}") String phone,
                                 @NotBlank @Size(min = 8, max = 72) String password) {}
    public record TeacherView(Long id, Long userId, String email, String fullName, String phone) {}
    public record SummaryView(long students, long teachers, long subjects, long exams, long results, long branches) {}
    public record Message(String message) {}
}
