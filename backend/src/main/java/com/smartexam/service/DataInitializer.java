package com.smartexam.service;

import com.smartexam.entity.Domain.*;
import com.smartexam.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {
    private final Branches branches;
    private final Semesters semesters;
    private final Users users;
    private final Students students;
    private final Teachers teachers;
    private final Subjects subjects;
    private final Exams exams;
    private final Questions questions;
    private final PasswordEncoder encoder;
    @Value("${SMARTEXAM_ADMIN_EMAIL:}") private String adminEmail;
    @Value("${SMARTEXAM_ADMIN_PASSWORD:}") private String adminPassword;
    @Value("${SMARTEXAM_DEMO:false}") private boolean demo;

    public DataInitializer(Branches branches, Semesters semesters, Users users, Students students,
                           Teachers teachers, Subjects subjects, Exams exams, Questions questions, PasswordEncoder encoder) {
        this.branches = branches; this.semesters = semesters; this.users = users; this.students = students;
        this.teachers = teachers; this.subjects = subjects; this.exams = exams; this.questions = questions; this.encoder = encoder;
    }

    @Override @Transactional
    public void run(String... args) {
        List<String[]> defaults = List.of(
            new String[]{"CSE", "Computer Science Engineering"}, new String[]{"ISE", "Information Science Engineering"},
            new String[]{"ECE", "Electronics and Communication Engineering"}, new String[]{"EEE", "Electrical and Electronics Engineering"},
            new String[]{"ME", "Mechanical Engineering"}, new String[]{"CE", "Civil Engineering"},
            new String[]{"AIML", "Artificial Intelligence and Machine Learning"});
        for (String[] row : defaults) {
            Branch branch = branches.findByCodeIgnoreCase(row[0]).orElseGet(() -> branches.save(new Branch(row[0], row[1])));
            for (int i = 1; i <= 8; i++) {
                int number = i;
                semesters.findByBranchIdAndNumber(branch.id, i).orElseGet(() -> semesters.save(new Semester(branch, number)));
            }
        }
        if (!adminEmail.isBlank() && !adminPassword.isBlank()) {
            users.findByEmailIgnoreCase(adminEmail).orElseGet(() -> users.save(new UserAccount(adminEmail.toLowerCase(), encoder.encode(adminPassword), Role.ADMIN)));
        }
        if (demo) seedDemo();
    }

    private void seedDemo() {
        if (users.existsByEmailIgnoreCase("teacher@smartexam.local")) return;
        Branch cse = branches.findByCodeIgnoreCase("CSE").orElseThrow();
        Semester semester = semesters.findByBranchIdAndNumber(cse.id, 4).orElseThrow();
        UserAccount teacherUser = users.save(new UserAccount("teacher@smartexam.local", encoder.encode("Teacher@123"), Role.TEACHER));
        Teacher teacher = teachers.save(new Teacher(teacherUser, "Demo Teacher", "9000000001"));
        UserAccount studentUser = users.save(new UserAccount("student@smartexam.local", encoder.encode("Student@123"), Role.STUDENT));
        students.save(new Student(studentUser, "Demo Student", "DEMO2024001", "9000000002", cse, semester));
        Subject subject = new Subject();
        subject.code = "DEMO-CSE401"; subject.name = "Foundations of Computing"; subject.branch = cse; subject.semester = semester;
        subject.description = "Local demonstration subject. Replace with your department syllabus."; subject.teacher = teacher;
        subject.totalMarks = 20; subject.passingMarks = 8; subject.durationMinutes = 20;
        subjects.save(subject);
        Exam exam = new Exam();
        exam.title = "DEMO — Computing fundamentals checkpoint";
        exam.subject = subject;
        exam.startTime = Instant.now().minusSeconds(60);
        exam.endTime = Instant.now().plusSeconds(30L * 24 * 60 * 60);
        exam.durationMinutes = 20;
        exam.totalMarks = 20;
        exam.passingMarks = 8;
        exams.save(exam);
        addDemoQuestion(exam, "Which data structure follows the FIFO principle?", "Stack", "Queue", "Tree", "Graph", "B", 4);
        addDemoQuestion(exam, "Which component executes program instructions?", "Compiler", "Processor", "Cache", "Register", "B", 4);
        addDemoQuestion(exam, "What is the worst-case lookup time in a balanced binary search tree?", "O(1)", "O(log n)", "O(n)", "O(n²)", "B", 4);
        addDemoQuestion(exam, "Which number system uses base 2?", "Decimal", "Octal", "Binary", "Hexadecimal", "C", 4);
        addDemoQuestion(exam, "Which structure is commonly used for recursive function calls?", "Queue", "Call stack", "Hash table", "Heap", "B", 4);
    }

    private void addDemoQuestion(Exam exam, String prompt, String a, String b, String c, String d, String correct, int marks) {
        Question q = new Question(); q.exam = exam; q.prompt = prompt; q.optionA = a; q.optionB = b;
        q.optionC = c; q.optionD = d; q.correctOption = correct; q.marks = marks; questions.save(q);
    }
}
