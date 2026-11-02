package com.smartexam.controller;

import com.smartexam.dto.Api.*;
import com.smartexam.service.AcademicService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin")
public class AdminController {
    private final AcademicService academic;
    public AdminController(AcademicService academic) { this.academic = academic; }
    @GetMapping("/summary") public SummaryView summary() { return academic.summary(); }
    @GetMapping("/branches") public List<BranchView> branches() { return academic.branches(); }
    @GetMapping("/branches/{branchId}/semesters") public List<SemesterView> semesters(@PathVariable Long branchId) { return academic.semesters(branchId); }
    @PostMapping("/branches") public BranchView addBranch(@Valid @RequestBody BranchInput input) { return academic.createBranch(input.code(), input.name()); }
    @GetMapping("/subjects") public List<SubjectView> subjects() { return academic.subjects(); }
    @PostMapping("/subjects") public SubjectView addSubject(@Valid @RequestBody SubjectRequest request) { return academic.createSubject(request); }
    @PutMapping("/subjects/{id}") public SubjectView updateSubject(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) { return academic.updateSubject(id, request); }
    @DeleteMapping("/subjects/{id}") public Message deleteSubject(@PathVariable Long id) { return academic.deleteSubject(id); }
    @GetMapping("/students") public List<UserView> students() { return academic.students(); }
    @GetMapping("/teachers") public List<TeacherView> teachers() { return academic.teachers(); }
    @PostMapping("/teachers") public TeacherView addTeacher(@Valid @RequestBody TeacherRequest request) { return academic.createTeacher(request); }
    @GetMapping("/exams") public List<ExamView> exams() { return academic.exams(); }
    @PutMapping("/exams/{examId}/multiple-attempts") public Message multipleAttempts(@PathVariable Long examId, @RequestBody MultipleAttemptsRequest request) {
        return academic.setMultipleAttempts(examId, request.enabled());
    }
    @GetMapping("/results") public List<ResultView> results() { return academic.results(); }

    public record BranchInput(@NotBlank String code, @NotBlank String name) {}
}
