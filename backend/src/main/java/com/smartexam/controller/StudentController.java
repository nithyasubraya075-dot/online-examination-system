package com.smartexam.controller;

import com.smartexam.dto.Api.*;
import com.smartexam.service.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    private final ExamService exams; private final DashboardService dashboard;
    public StudentController(ExamService exams, DashboardService dashboard) { this.exams = exams; this.dashboard = dashboard; }
    @GetMapping("/dashboard") public DashboardView dashboard(Authentication auth) { return dashboard.studentDashboard(auth.getName()); }
    @GetMapping("/subjects") public List<SubjectView> subjects(Authentication auth) { return exams.studentSubjects(auth.getName()); }
    @GetMapping("/exams") public List<ExamView> exams(Authentication auth) { return exams.studentExams(auth.getName()); }
    @PostMapping("/exams/{examId}/start") public AttemptView start(Authentication auth, @PathVariable Long examId) { return exams.start(auth.getName(), examId); }
    @GetMapping("/attempts/{attemptId}") public AttemptView attempt(Authentication auth, @PathVariable Long attemptId) { return exams.getAttempt(auth.getName(), attemptId); }
    @PutMapping("/attempts/{attemptId}/answers") public Message answer(Authentication auth, @PathVariable Long attemptId, @Valid @RequestBody AnswerRequest request) {
        return exams.saveAnswer(auth.getName(), attemptId, request);
    }
    @PostMapping("/attempts/{attemptId}/submit") public ResultView submit(Authentication auth, @PathVariable Long attemptId) { return exams.submit(auth.getName(), attemptId); }
    @GetMapping("/results") public List<ResultView> results(Authentication auth) { return exams.studentResults(auth.getName()); }
}
