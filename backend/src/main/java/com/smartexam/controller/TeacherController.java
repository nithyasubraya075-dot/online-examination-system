package com.smartexam.controller;

import com.smartexam.dto.Api.*;
import com.smartexam.service.ExamService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    private final ExamService exams;
    public TeacherController(ExamService exams) { this.exams = exams; }
    @GetMapping("/subjects") public List<SubjectView> subjects(Authentication auth) { return exams.teacherSubjects(auth.getName()); }
    @GetMapping("/exams") public List<ExamView> exams(Authentication auth) { return exams.teacherExams(auth.getName()); }
    @PostMapping("/exams") public ExamView createExam(Authentication auth, @Valid @RequestBody ExamRequest request) { return exams.createExam(auth.getName(), request); }
    @PutMapping("/exams/{examId}") public ExamView updateExam(Authentication auth, @PathVariable Long examId, @Valid @RequestBody ExamRequest request) {
        return exams.updateExam(auth.getName(), examId, request);
    }
    @DeleteMapping("/exams/{examId}") public Message deleteExam(Authentication auth, @PathVariable Long examId) { return exams.deleteExam(auth.getName(), examId); }
    @GetMapping("/exams/{examId}/questions") public List<QuestionView> questions(Authentication auth, @PathVariable Long examId) { return exams.teacherQuestions(auth.getName(), examId); }
    @PostMapping("/exams/{examId}/questions") public QuestionView add(Authentication auth, @PathVariable Long examId, @Valid @RequestBody QuestionRequest request) {
        return exams.addQuestion(auth.getName(), examId, request);
    }
    @PutMapping("/exams/{examId}/questions/{questionId}") public QuestionView update(Authentication auth, @PathVariable Long examId, @PathVariable Long questionId, @Valid @RequestBody QuestionRequest request) {
        return exams.updateQuestion(auth.getName(), examId, questionId, request);
    }
    @DeleteMapping("/exams/{examId}/questions/{questionId}") public Message delete(Authentication auth, @PathVariable Long examId, @PathVariable Long questionId) {
        return exams.deleteQuestion(auth.getName(), examId, questionId);
    }
    @GetMapping("/results") public List<ResultView> results(Authentication auth) { return exams.teacherResults(auth.getName()); }
}
