package com.smartexam.entity;

import jakarta.persistence.*;
import java.time.Instant;

public final class Domain {
    private Domain() {}

    public enum Role { STUDENT, TEACHER, ADMIN }

    @Entity @Table(name = "users")
    public static class UserAccount {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(nullable = false, unique = true) public String email;
        @Column(nullable = false) public String passwordHash;
        @Enumerated(EnumType.STRING) @Column(nullable = false) public Role role;
        @Column(nullable = false) public boolean enabled = true;
        protected UserAccount() {}
        public UserAccount(String email, String passwordHash, Role role) { this.email = email; this.passwordHash = passwordHash; this.role = role; }
    }

    @Entity @Table(name = "branches")
    public static class Branch {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(nullable = false, unique = true) public String code;
        @Column(nullable = false, unique = true) public String name;
        protected Branch() {}
        public Branch(String code, String name) { this.code = code; this.name = name; }
    }

    @Entity @Table(name = "semesters", uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "number"}))
    public static class Semester {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @ManyToOne(optional = false) public Branch branch;
        @Column(nullable = false) public int number;
        protected Semester() {}
        public Semester(Branch branch, int number) { this.branch = branch; this.number = number; }
    }

    @Entity @Table(name = "students")
    public static class Student {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @OneToOne(optional = false) @JoinColumn(unique = true) public UserAccount user;
        @Column(nullable = false) public String fullName;
        @Column(nullable = false, unique = true) public String usn;
        @Column(nullable = false) public String phone;
        @ManyToOne(optional = false) public Branch branch;
        @ManyToOne(optional = false) public Semester semester;
        protected Student() {}
        public Student(UserAccount user, String fullName, String usn, String phone, Branch branch, Semester semester) {
            this.user = user; this.fullName = fullName; this.usn = usn; this.phone = phone; this.branch = branch; this.semester = semester;
        }
    }

    @Entity @Table(name = "teachers")
    public static class Teacher {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @OneToOne(optional = false) @JoinColumn(unique = true) public UserAccount user;
        @Column(nullable = false) public String fullName;
        @Column(nullable = false) public String phone;
        protected Teacher() {}
        public Teacher(UserAccount user, String fullName, String phone) { this.user = user; this.fullName = fullName; this.phone = phone; }
    }

    @Entity @Table(name = "subjects")
    public static class Subject {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(nullable = false, unique = true) public String code;
        @Column(nullable = false) public String name;
        @ManyToOne(optional = false) public Branch branch;
        @ManyToOne(optional = false) public Semester semester;
        @Column(length = 1500) public String description;
        @ManyToOne public Teacher teacher;
        public int totalMarks = 100;
        public int passingMarks = 40;
        public int durationMinutes = 60;
        public boolean available = true;
        public Subject() {}
    }

    @Entity @Table(name = "exams")
    public static class Exam {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(nullable = false) public String title;
        @ManyToOne(optional = false) public Subject subject;
        @Column(nullable = false) public Instant startTime;
        @Column(nullable = false) public Instant endTime;
        @Column(nullable = false) public int durationMinutes;
        @Column(nullable = false) public int totalMarks;
        @Column(nullable = false) public int passingMarks;
        public boolean allowMultipleAttempts;
        @Column(nullable = false) public String status = "SCHEDULED";
        public Exam() {}
    }

    @Entity @Table(name = "questions")
    public static class Question {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @ManyToOne(optional = false) public Exam exam;
        @Column(nullable = false, length = 2000) public String prompt;
        @Column(nullable = false) public String optionA;
        @Column(nullable = false) public String optionB;
        @Column(nullable = false) public String optionC;
        @Column(nullable = false) public String optionD;
        @Column(nullable = false) public String correctOption;
        @Column(nullable = false) public int marks;
        public Question() {}
    }

    @Entity @Table(name = "exam_attempts")
    public static class Attempt {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @ManyToOne(optional = false) public Exam exam;
        @ManyToOne(optional = false) public Student student;
        @Column(nullable = false) public Instant startedAt;
        @Column(nullable = false) public Instant deadline;
        public Instant submittedAt;
        public Attempt() {}
    }

    @Entity @Table(name = "student_answers", uniqueConstraints = @UniqueConstraint(columnNames = {"attempt_id", "question_id"}))
    public static class Answer {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @ManyToOne(optional = false) public Attempt attempt;
        @ManyToOne(optional = false) public Question question;
        public String selectedOption;
        @Column(nullable = false) public boolean markedForReview;
        protected Answer() {}
        public Answer(Attempt attempt, Question question, String selectedOption) { this.attempt = attempt; this.question = question; this.selectedOption = selectedOption; }
    }

    @Entity @Table(name = "results")
    public static class Result {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @OneToOne(optional = false) @JoinColumn(unique = true) public Attempt attempt;
        @Column(nullable = false) public int correct;
        @Column(nullable = false) public int wrong;
        @Column(nullable = false) public int unanswered;
        @Column(nullable = false) public double obtainedMarks;
        @Column(nullable = false) public double percentage;
        @Column(nullable = false) public boolean passed;
        protected Result() {}
        public Result(Attempt attempt, int correct, int wrong, int unanswered, double obtainedMarks, double percentage, boolean passed) {
            this.attempt = attempt; this.correct = correct; this.wrong = wrong; this.unanswered = unanswered;
            this.obtainedMarks = obtainedMarks; this.percentage = percentage; this.passed = passed;
        }
    }
}
