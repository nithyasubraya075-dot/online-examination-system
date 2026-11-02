package com.smartexam.service;

import com.smartexam.dto.Api.*;
import com.smartexam.entity.Domain.*;
import com.smartexam.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final Users users;
    private final Students students;
    private final Teachers teachers;
    private final Branches branches;
    private final Semesters semesters;
    private final PasswordEncoder encoder;

    public AuthService(Users users, Students students, Teachers teachers, Branches branches, Semesters semesters, PasswordEncoder encoder) {
        this.users = users; this.students = students; this.teachers = teachers;
        this.branches = branches; this.semesters = semesters; this.encoder = encoder;
    }

    @Transactional
    public UserView register(RegisterRequest input) {
        if (!input.password().equals(input.confirmPassword())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
        if (users.existsByEmailIgnoreCase(input.email())) throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered.");
        String usn = input.usn().trim().toUpperCase();
        if (students.existsByUsnIgnoreCase(usn)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That USN is already registered.");
        Branch branch = branches.findById(input.branchId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid branch."));
        Semester semester = semesters.findByBranchIdAndNumber(branch.id, input.semester())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid semester."));
        UserAccount user = users.save(new UserAccount(input.email().trim().toLowerCase(), encoder.encode(input.password()), Role.STUDENT));
        Student student = students.save(new Student(user, input.fullName().trim(), usn,
                input.phone().trim(), branch, semester));
        return new UserView(user.id, user.email, user.role.name(), student.fullName, student.usn, branch.name, semester.number);
    }

    @Transactional(readOnly = true)
    public UserView current(String email) {
        UserAccount user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.role == Role.STUDENT) {
            Student s = students.findByUserId(user.id).orElseThrow();
            return new UserView(user.id, user.email, user.role.name(), s.fullName, s.usn, s.branch.name, s.semester.number);
        }
        if (user.role == Role.TEACHER) {
            Teacher teacher = teachers.findByUserId(user.id).orElseThrow();
            return new UserView(user.id, user.email, user.role.name(), teacher.fullName, null, null, null);
        }
        return new UserView(user.id, user.email, user.role.name(), "Administrator", null, null, null);
    }
}
