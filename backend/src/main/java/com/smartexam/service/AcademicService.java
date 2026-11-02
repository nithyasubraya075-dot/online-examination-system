package com.smartexam.service;

import com.smartexam.dto.Api.*;
import com.smartexam.entity.Domain.*;
import com.smartexam.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.time.Instant;

@Service
public class AcademicService {
    private final Branches branches; private final Semesters semesters; private final Subjects subjects;
    private final Teachers teachers; private final Users users; private final Students students;
    private final Exams exams; private final Questions questions; private final Results results;
    private final PasswordEncoder encoder;

    public AcademicService(Branches branches, Semesters semesters, Subjects subjects, Teachers teachers,
                           Users users, Students students, Exams exams, Questions questions, Results results,
                           PasswordEncoder encoder) {
        this.branches = branches; this.semesters = semesters; this.subjects = subjects; this.teachers = teachers;
        this.users = users; this.students = students; this.exams = exams; this.questions = questions;
        this.results = results; this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<BranchView> branches() { return branches.findAll().stream().map(b -> new BranchView(b.id, b.code, b.name)).toList(); }

    @Transactional(readOnly = true)
    public List<SemesterView> semesters(Long branchId) {
        if (!branches.existsById(branchId)) throw notFound("Branch not found.");
        Instant now = Instant.now();
        return semesters.findByBranchIdOrderByNumber(branchId).stream().map(semester -> {
            List<Exam> exams = this.exams.findBySubjectBranchIdAndSubjectSemesterNumber(branchId, semester.number);
            return new SemesterView(semester.id, semester.number,
                    subjects.findByBranchIdAndSemesterNumber(branchId, semester.number).size(), exams.size(),
                    exams.stream().filter(e -> e.startTime.isAfter(now)).count(),
                    exams.stream().filter(e -> !e.startTime.isAfter(now) && !e.endTime.isBefore(now)).count(),
                    exams.stream().filter(e -> e.endTime.isBefore(now)).count());
        }).toList();
    }

    @Transactional
    public BranchView createBranch(String code, String name) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{2,12}") || name == null || name.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a branch name and a 2–12 character branch code.");
        if (branches.findByCodeIgnoreCase(code).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "That branch code already exists.");
        Branch branch = branches.save(new Branch(code.toUpperCase(), name.trim()));
        for (int n = 1; n <= 8; n++) semesters.save(new Semester(branch, n));
        return new BranchView(branch.id, branch.code, branch.name);
    }

    @Transactional(readOnly = true)
    public List<SubjectView> subjects() { return subjects.findAll().stream().map(this::subjectView).toList(); }

    @Transactional
    public SubjectView createSubject(SubjectRequest input) {
        if (subjects.existsByCodeIgnoreCase(input.code())) throw new ResponseStatusException(HttpStatus.CONFLICT, "That subject code already exists.");
        Subject subject = new Subject();
        applySubject(subject, input);
        return subjectView(subjects.save(subject));
    }

    @Transactional
    public SubjectView updateSubject(Long id, SubjectRequest input) {
        Subject subject = subjects.findById(id).orElseThrow(() -> notFound("Subject not found."));
        if (!subject.code.equalsIgnoreCase(input.code()) && subjects.existsByCodeIgnoreCase(input.code()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That subject code already exists.");
        applySubject(subject, input);
        return subjectView(subjects.save(subject));
    }

    @Transactional
    public Message deleteSubject(Long id) {
        Subject subject = subjects.findById(id).orElseThrow(() -> notFound("Subject not found."));
        if (exams.countBySubjectId(id) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "A subject with exams cannot be deleted. Mark it unavailable instead.");
        subjects.delete(subject);
        return new Message("Subject deleted.");
    }

    @Transactional
    public TeacherView createTeacher(TeacherRequest input) {
        if (users.existsByEmailIgnoreCase(input.email())) throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already in use.");
        UserAccount user = users.save(new UserAccount(input.email().trim().toLowerCase(), encoder.encode(input.password()), Role.TEACHER));
        Teacher teacher = teachers.save(new Teacher(user, input.fullName().trim(), input.phone().trim()));
        return new TeacherView(teacher.id, user.id, user.email, teacher.fullName, teacher.phone);
    }

    @Transactional(readOnly = true)
    public List<UserView> students() {
        return students.findAll().stream().map(s -> new UserView(s.user.id, s.user.email, "STUDENT",
                s.fullName, s.usn, s.branch.name, s.semester.number)).toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherView> teachers() {
        return teachers.findAll().stream().map(t -> new TeacherView(t.id, t.user.id, t.user.email, t.fullName, t.phone)).toList();
    }

    @Transactional(readOnly = true)
    public SummaryView summary() {
        return new SummaryView(students.count(), teachers.count(), subjects.count(), exams.count(), results.count(), branches.count());
    }

    @Transactional(readOnly = true)
    public List<ExamView> exams() {
        return exams.findAll().stream().map(e -> new ExamView(e.id, e.title, e.subject.name, e.subject.code,
                e.subject.branch.name, e.subject.semester.number, e.startTime, e.endTime, e.durationMinutes,
                e.totalMarks, e.passingMarks, examStatus(e), questions.countByExamId(e.id), e.allowMultipleAttempts, false, null)).toList();
    }

    @Transactional
    public Message setMultipleAttempts(Long examId, boolean enabled) {
        Exam exam = exams.findById(examId).orElseThrow(() -> notFound("Exam not found."));
        exam.allowMultipleAttempts = enabled;
        exams.save(exam);
        return new Message(enabled ? "One additional attempt is now allowed." : "Additional attempts are disabled.");
    }

    @Transactional(readOnly = true)
    public List<ResultView> results() {
        return results.findAllByOrderByIdDesc().stream().map(r -> {
            Attempt a = r.attempt; Exam e = a.exam;
            return new ResultView(r.id, a.id, e.title, e.subject.name, e.subject.code, e.subject.branch.name,
                    e.subject.semester.number, a.student.fullName, a.student.usn, a.submittedAt, r.correct,
                    r.wrong, r.unanswered, r.obtainedMarks, e.totalMarks, r.percentage, r.passed);
        }).toList();
    }

    private void applySubject(Subject s, SubjectRequest input) {
        if (input.passingMarks() > input.totalMarks()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passing marks cannot exceed total marks.");
        Branch branch = branches.findById(input.branchId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid branch."));
        Semester semester = semesters.findByBranchIdAndNumber(branch.id, input.semester())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid semester."));
        Teacher teacher = input.teacherId() == null ? null : teachers.findById(input.teacherId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid teacher."));
        s.code = input.code().trim().toUpperCase(); s.name = input.name().trim(); s.branch = branch; s.semester = semester;
        s.description = input.description(); s.teacher = teacher; s.totalMarks = input.totalMarks();
        s.passingMarks = input.passingMarks(); s.durationMinutes = input.durationMinutes(); s.available = input.available();
    }

    private SubjectView subjectView(Subject s) {
        return new SubjectView(s.id, s.code, s.name, s.branch.name, s.branch.code, s.semester.number,
                s.description, s.teacher == null ? "Unassigned" : s.teacher.fullName, s.teacher == null ? null : s.teacher.id, s.totalMarks,
                s.passingMarks, s.durationMinutes, s.available, exams.countBySubjectId(s.id));
    }

    private String examStatus(Exam e) {
        Instant now = Instant.now();
        return now.isBefore(e.startTime) ? "UPCOMING" : now.isAfter(e.endTime) ? "COMPLETED" : "AVAILABLE";
    }

    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
