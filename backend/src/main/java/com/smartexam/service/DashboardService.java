package com.smartexam.service;

import com.smartexam.dto.Api.*;
import com.smartexam.entity.Domain.*;
import com.smartexam.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

@Service
public class DashboardService {
    private final Users users; private final Students students; private final Subjects subjects;
    private final Exams exams; private final Attempts attempts; private final Results results; private final Branches branches;
    public DashboardService(Users users, Students students, Subjects subjects, Exams exams, Attempts attempts, Results results, Branches branches) {
        this.users = users; this.students = students; this.subjects = subjects; this.exams = exams;
        this.attempts = attempts; this.results = results; this.branches = branches;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> publicStats() {
        return Map.of("branches", branches.count(), "students", students.count(), "subjects", subjects.count(), "exams", exams.count());
    }

    @Transactional(readOnly = true)
    public DashboardView studentDashboard(String email) {
        UserAccount user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Student s = students.findByUserId(user.id).orElseThrow();
        List<Subject> mySubjects = subjects.findByBranchIdAndSemesterNumberAndAvailableTrue(s.branch.id, s.semester.number);
        List<Exam> myExams = exams.findBySubjectBranchIdAndSubjectSemesterNumber(s.branch.id, s.semester.number).stream()
                .filter(e -> e.subject.available).toList();
        List<Attempt> myAttempts = attempts.findByStudentIdOrderByStartedAtDesc(s.id);
        List<Result> myResults = myAttempts.stream().map(a -> results.findByAttemptId(a.id).orElse(null)).filter(Objects::nonNull).toList();
        Instant now = Instant.now();
        long upcoming = myExams.stream().filter(e -> e.startTime.isAfter(now)).count();
        long available = myExams.stream().filter(e -> !e.startTime.isAfter(now) && !e.endTime.isBefore(now)).count();
        double average = myResults.isEmpty() ? 0 : myResults.stream().mapToDouble(r -> r.percentage).average().orElse(0);
        List<ResultView> recent = myResults.stream().limit(5).map(r -> {
            Attempt a = r.attempt; Exam e = a.exam;
            return new ResultView(r.id, a.id, e.title, e.subject.name, e.subject.code, e.subject.branch.name,
                    e.subject.semester.number, a.student.fullName, a.student.usn, a.submittedAt, r.correct,
                    r.wrong, r.unanswered, r.obtainedMarks, e.totalMarks, r.percentage, r.passed);
        }).toList();
        UserView userView = new UserView(user.id, user.email, "STUDENT", s.fullName, s.usn, s.branch.name, s.semester.number);
        return new DashboardView(userView, mySubjects.size(), available, upcoming, myResults.size(), average,
                myResults.stream().filter(r -> r.passed).count(), myResults.stream().filter(r -> !r.passed).count(), recent);
    }
}
