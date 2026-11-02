package com.smartexam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartexam.entity.Domain.*;
import com.smartexam.repository.*;
import com.smartexam.service.ExamService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:smartexam;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "SMARTEXAM_DEMO=false"
})
@AutoConfigureMockMvc
class SMARTEXAMApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Branches branches;
    @Autowired Semesters semesters;
    @Autowired Users users;
    @Autowired Students students;
    @Autowired Teachers teachers;
    @Autowired Subjects subjects;
    @Autowired Exams exams;
    @Autowired Questions questions;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ExamService examService;

    @Test
    void seedsEveryDefaultBranchAndAllSemesters() {
        assertThat(branches.count()).isEqualTo(7);
        for (Branch branch : branches.findAll()) {
            assertThat(semesters.findByBranchIdOrderByNumber(branch.id))
                    .extracting(s -> s.number).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        }
        examService.submitExpiredAttempts();
    }

    @Test
    void registrationHashesPasswordAndRejectsDuplicateEmail() throws Exception {
        Branch branch = branches.findByCodeIgnoreCase("CSE").orElseThrow();
        String request = mapper.writeValueAsString(new RegisterPayload("Test Student", "TEST2026001",
                "test.student@smartexam.local", "9000000000", branch.id, 4, "VeryStrong@123", "VeryStrong@123"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
        UserAccount saved = users.findByEmailIgnoreCase("test.student@smartexam.local").orElseThrow();
        assertThat(saved.passwordHash).isNotEqualTo("VeryStrong@123");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void protectsAdministrativeEndpoints() throws Exception {
        mvc.perform(get("/api/admin/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/stats")).andExpect(status().isOk()).andExpect(jsonPath("$.branches").value(7));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("<title>SMARTEXAM")));
        mvc.perform(get("/assets/app.js")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/api/auth/csrf")));
    }

    @Test
    void adminCanReviewDatabaseBackedSemesterExamCounts() throws Exception {
        Branch branch = branches.findByCodeIgnoreCase("CSE").orElseThrow();
        mvc.perform(get("/api/admin/branches/{id}/semesters", branch.id).with(user("admin@smartexam.local").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(8)))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].subjectCount").value(0));
    }

    @Test
    void signsInThroughTheSessionLoginEndpoint() throws Exception {
        users.save(new UserAccount("login.student@smartexam.local", passwordEncoder.encode("VeryStrong@123"), Role.STUDENT));
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", "login.student@smartexam.local").param("password", "VeryStrong@123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void studentAttemptHidesAnswersPersistsReviewAndScoresOnServer() throws Exception {
        Branch branch = branches.findByCodeIgnoreCase("CSE").orElseThrow();
        Semester semester = semesters.findByBranchIdAndNumber(branch.id, 4).orElseThrow();
        UserAccount teacherUser = users.save(new UserAccount("exam.teacher@smartexam.local", passwordEncoder.encode("Teacher@123"), Role.TEACHER));
        Teacher teacher = teachers.save(new Teacher(teacherUser, "Exam Teacher", "9000000011"));
        UserAccount studentUser = users.save(new UserAccount("exam.student@smartexam.local", passwordEncoder.encode("Student@123"), Role.STUDENT));
        Student student = students.save(new Student(studentUser, "Exam Student", "EXAM2026001", "9000000012", branch, semester));
        Subject subject = new Subject();
        subject.code = "TEST-CSE401"; subject.name = "Test Subject"; subject.branch = branch; subject.semester = semester;
        subject.teacher = teacher; subject.available = true;
        subject.totalMarks = 4; subject.passingMarks = 2; subject.durationMinutes = 15;
        subject = subjects.save(subject);
        Exam exam = new Exam();
        exam.title = "Test Assessment"; exam.subject = subject; exam.startTime = Instant.now().minusSeconds(10);
        exam.endTime = Instant.now().plusSeconds(600); exam.durationMinutes = 15; exam.totalMarks = 4;
        exam.passingMarks = 2; exam.status = "SCHEDULED";
        exam = exams.save(exam);
        Question first = question(exam, "One plus one?", "1", "2", "3", "4", "B", 2);
        Question second = question(exam, "Select a prime number.", "4", "6", "7", "8", "C", 2);
        questions.save(first); questions.save(second);

        var studentAuth = user("exam.student@smartexam.local").roles("STUDENT");
        var start = mvc.perform(post("/api/student/exams/{id}/start", exam.id).with(studentAuth).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].correctOption").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        long attemptId = mapper.readTree(start.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/student/attempts/{id}/answers", attemptId).with(studentAuth).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AnswerPayload(first.id, "B", false))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/student/attempts/{id}/answers", attemptId).with(studentAuth).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AnswerPayload(second.id, null, true))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/student/attempts/{id}", attemptId).with(studentAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answers[1].markedForReview").value(true));
        mvc.perform(post("/api/student/attempts/{id}/submit", attemptId).with(studentAuth).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(1))
                .andExpect(jsonPath("$.wrong").value(0))
                .andExpect(jsonPath("$.unanswered").value(1))
                .andExpect(jsonPath("$.obtainedMarks").value(2))
                .andExpect(jsonPath("$.passed").value(true));
        mvc.perform(post("/api/student/exams/{id}/start", exam.id).with(studentAuth).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/exams/{id}/multiple-attempts", exam.id).with(user("admin@smartexam.local").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/student/exams/{id}/start", exam.id).with(studentAuth).with(csrf()))
                .andExpect(status().isOk());
    }

    private Question question(Exam exam, String prompt, String a, String b, String c, String d, String correct, int marks) {
        Question q = new Question(); q.exam = exam; q.prompt = prompt; q.optionA = a; q.optionB = b;
        q.optionC = c; q.optionD = d; q.correctOption = correct; q.marks = marks; return q;
    }

    private record RegisterPayload(String fullName, String usn, String email, String phone, Long branchId,
                                   int semester, String password, String confirmPassword) {}
    private record AnswerPayload(Long questionId, String selectedOption, boolean markedForReview) {}
}
