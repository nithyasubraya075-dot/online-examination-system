package com.smartexam.service;

import com.smartexam.dto.Api.*;
import com.smartexam.entity.Domain.*;
import com.smartexam.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamService {
    private final Users users; private final Students students; private final Teachers teachers;
    private final Subjects subjects; private final Exams exams; private final Questions questions;
    private final Attempts attempts; private final Answers answers; private final Results results;

    public ExamService(Users users, Students students, Teachers teachers, Subjects subjects, Exams exams,
                      Questions questions, Attempts attempts, Answers answers, Results results) {
        this.users = users; this.students = students; this.teachers = teachers; this.subjects = subjects;
        this.exams = exams; this.questions = questions; this.attempts = attempts; this.answers = answers; this.results = results;
    }

    @Transactional(readOnly = true)
    public List<SubjectView> studentSubjects(String email) {
        Student s = student(email);
        return subjects.findByBranchIdAndSemesterNumberAndAvailableTrue(s.branch.id, s.semester.number).stream().map(this::subjectView).toList();
    }

    @Transactional(readOnly = true)
    public List<ExamView> studentExams(String email) {
        Student s = student(email);
        return exams.findBySubjectBranchIdAndSubjectSemesterNumber(s.branch.id, s.semester.number).stream()
                .filter(e -> e.subject.available).map(e -> examView(e, s)).toList();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AttemptView start(String email, Long examId) {
        Student s = students.lockByUserId(user(email).id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Student profile is not configured."));
        Exam exam = exams.findById(examId).orElseThrow(() -> notFound("Exam not found."));
        if (!exam.subject.available || !exam.subject.branch.id.equals(s.branch.id) || exam.subject.semester.number != s.semester.number)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This exam is not assigned to your branch and semester.");
        Instant now = Instant.now();
        if (now.isBefore(exam.startTime) || now.isAfter(exam.endTime))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This exam is not currently available.");
        if (questions.countByExamId(exam.id) == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "This exam has no questions yet.");
        int configuredQuestionMarks = questions.findByExamIdOrderById(exam.id).stream().mapToInt(q -> q.marks).sum();
        if (configuredQuestionMarks != exam.totalMarks)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This exam's question marks do not add up to its configured total. Please contact your teacher.");
        List<Attempt> existing = attempts.lockByStudentIdOrderByStartedAtDesc(s.id).stream().filter(a -> a.exam.id.equals(exam.id)).toList();
        Optional<Attempt> open = existing.stream().filter(a -> a.submittedAt == null).findFirst();
        if (open.isPresent()) {
            Attempt attempt = attempts.lockByIdAndStudentId(open.get().id, s.id)
                    .orElseThrow(() -> notFound("Exam attempt not found."));
            if (attempt.submittedAt == null) {
                if (!now.isBefore(attempt.deadline)) {
                    submitAttempt(attempt, now);
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Your exam time expired and your answers were submitted.");
                }
                return attemptView(attempt);
            }
        }
        int attemptLimit = exam.allowMultipleAttempts ? 2 : 1;
        if (existing.size() >= attemptLimit)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already attempted this exam.");
        Instant deadline = now.plusSeconds(exam.durationMinutes * 60L);
        if (deadline.isAfter(exam.endTime)) deadline = exam.endTime;
        Attempt attempt = new Attempt();
        attempt.exam = exam; attempt.student = s; attempt.startedAt = now; attempt.deadline = deadline;
        attempt = attempts.save(attempt);
        return attemptView(attempt);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AttemptView getAttempt(String email, Long attemptId) {
        Attempt a = ownedAttempt(email, attemptId);
        if (a.submittedAt == null && !Instant.now().isBefore(a.deadline)) {
            submitAttempt(a, Instant.now());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your exam time expired and your answers were submitted.");
        }
        if (a.submittedAt != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This exam attempt has been submitted.");
        return attemptView(a);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Message saveAnswer(String email, Long attemptId, AnswerRequest input) {
        Attempt a = ownedAttempt(email, attemptId);
        requireActive(a);
        Question q = questions.findById(input.questionId()).orElseThrow(() -> notFound("Question not found."));
        if (!q.exam.id.equals(a.exam.id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question does not belong to this exam.");
        Optional<Answer> current = answers.findByAttemptIdAndQuestionId(a.id, q.id);
        boolean hasSelection = input.selectedOption() != null && !input.selectedOption().isBlank();
        if (!hasSelection && !input.markedForReview()) {
            current.ifPresent(answers::delete);
        } else if (hasSelection && !input.selectedOption().matches("[ABCD]")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose one of the four answer options.");
        } else if (current.isPresent()) {
            current.get().selectedOption = hasSelection ? input.selectedOption() : null;
            current.get().markedForReview = input.markedForReview();
            answers.save(current.get());
        } else {
            Answer answer = new Answer(a, q, hasSelection ? input.selectedOption() : null);
            answer.markedForReview = input.markedForReview();
            answers.save(answer);
        }
        return new Message("Answer saved.");
    }

    @Transactional
    public ResultView submit(String email, Long attemptId) {
        Attempt a = ownedAttempt(email, attemptId);
        Result result = results.findByAttemptId(a.id).orElseGet(() -> submitAttempt(a, Instant.now()));
        return resultView(result);
    }

    @Transactional(readOnly = true)
    public List<ResultView> studentResults(String email) {
        Student s = student(email);
        return attempts.findByStudentIdOrderByStartedAtDesc(s.id).stream()
                .map(a -> results.findByAttemptId(a.id).orElse(null)).filter(Objects::nonNull).map(this::resultView).toList();
    }

    @Transactional(readOnly = true)
    public List<SubjectView> teacherSubjects(String email) {
        return subjects.findByTeacherUserId(user(email).id).stream().map(this::subjectView).toList();
    }

    @Transactional(readOnly = true)
    public List<ExamView> teacherExams(String email) {
        return exams.findBySubjectTeacherUserId(user(email).id).stream().map(e -> examView(e, null)).toList();
    }

    @Transactional
    public ExamView createExam(String email, ExamRequest input) {
        Subject subject = ownedSubject(email, input.subjectId());
        if (!input.endTime().isAfter(input.startTime())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time.");
        if (input.passingMarks() > input.totalMarks()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passing marks cannot exceed total marks.");
        Exam exam = new Exam(); exam.title = input.title().trim(); exam.subject = subject; exam.startTime = input.startTime();
        exam.endTime = input.endTime(); exam.durationMinutes = input.durationMinutes(); exam.totalMarks = input.totalMarks();
        exam.passingMarks = input.passingMarks(); exam.allowMultipleAttempts = false;
        exam.status = "SCHEDULED";
        return examView(exams.save(exam), null);
    }

    @Transactional
    public ExamView updateExam(String email, Long examId, ExamRequest input) {
        Exam exam = ownedExam(email, examId);
        if (hasAttempts(exam.id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An examination cannot be changed after a student attempt.");
        Subject subject = ownedSubject(email, input.subjectId());
        if (!input.endTime().isAfter(input.startTime())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time.");
        if (input.passingMarks() > input.totalMarks()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passing marks cannot exceed total marks.");
        exam.title = input.title().trim(); exam.subject = subject; exam.startTime = input.startTime(); exam.endTime = input.endTime();
        exam.durationMinutes = input.durationMinutes(); exam.totalMarks = input.totalMarks(); exam.passingMarks = input.passingMarks();
        return examView(exams.save(exam), null);
    }

    @Transactional
    public Message deleteExam(String email, Long examId) {
        Exam exam = ownedExam(email, examId);
        if (hasAttempts(exam.id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An examination with student attempts cannot be deleted.");
        questions.deleteAll(questions.findByExamIdOrderById(exam.id));
        exams.delete(exam);
        return new Message("Examination deleted.");
    }

    @Transactional(readOnly = true)
    public List<QuestionView> teacherQuestions(String email, Long examId) {
        ownedExam(email, examId);
        return questions.findByExamIdOrderById(examId).stream().map(q -> questionView(q, true)).toList();
    }

    @Transactional
    public QuestionView addQuestion(String email, Long examId, QuestionRequest input) {
        Exam e = ownedExam(email, examId);
        if (hasAttempts(e.id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Questions cannot be changed after an attempt.");
        int existingMarks = questions.findByExamIdOrderById(e.id).stream().mapToInt(q -> q.marks).sum();
        if (existingMarks + input.marks() > e.totalMarks)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question marks cannot exceed the exam total.");
        Question q = new Question(); q.exam = e; apply(q, input);
        return questionView(questions.save(q), true);
    }

    @Transactional
    public QuestionView updateQuestion(String email, Long examId, Long questionId, QuestionRequest input) {
        Exam e = ownedExam(email, examId);
        Question q = questions.findById(questionId).filter(x -> x.exam.id.equals(e.id)).orElseThrow(() -> notFound("Question not found."));
        if (hasAttempts(e.id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Questions cannot be changed after an attempt.");
        int remainingMarks = questions.findByExamIdOrderById(e.id).stream().filter(x -> !x.id.equals(q.id)).mapToInt(x -> x.marks).sum();
        if (remainingMarks + input.marks() > e.totalMarks)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question marks cannot exceed the exam total.");
        apply(q, input);
        return questionView(questions.save(q), true);
    }

    @Transactional
    public Message deleteQuestion(String email, Long examId, Long questionId) {
        Exam e = ownedExam(email, examId);
        Question q = questions.findById(questionId).filter(x -> x.exam.id.equals(e.id)).orElseThrow(() -> notFound("Question not found."));
        if (hasAttempts(e.id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Questions cannot be changed after an attempt.");
        questions.delete(q);
        return new Message("Question deleted.");
    }

    @Transactional(readOnly = true)
    public List<ResultView> teacherResults(String email) {
        Long teacherId = user(email).id;
        return results.findAllByOrderByIdDesc().stream().filter(r -> r.attempt.exam.subject.teacher != null
                && r.attempt.exam.subject.teacher.user.id.equals(teacherId)).map(this::resultView).toList();
    }

    @Transactional
    public void submitExpiredAttempts() {
        Instant now = Instant.now();
        attempts.lockExpiredAttempts(now).forEach(attempt -> submitAttempt(attempt, now));
    }

    private Result submitAttempt(Attempt a, Instant now) {
        if (a.submittedAt != null) return results.findByAttemptId(a.id).orElseThrow();
        a.submittedAt = now; attempts.save(a);
        List<Question> all = questions.findByExamIdOrderById(a.exam.id);
        Map<Long, String> selected = answers.findByAttemptId(a.id).stream().filter(x -> x.selectedOption != null)
                .collect(Collectors.toMap(x -> x.question.id, x -> x.selectedOption));
        int correct = 0, wrong = 0, unanswered = 0, score = 0;
        for (Question q : all) {
            String answer = selected.get(q.id);
            if (answer == null) unanswered++;
            else if (answer.equals(q.correctOption)) { correct++; score += q.marks; }
            else wrong++;
        }
        double percentage = a.exam.totalMarks == 0 ? 0 : score * 100.0 / a.exam.totalMarks;
        return results.save(new Result(a, correct, wrong, unanswered, score, percentage, score >= a.exam.passingMarks));
    }

    private void requireActive(Attempt a) {
        if (a.submittedAt != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This exam attempt has been submitted.");
        if (!Instant.now().isBefore(a.deadline)) {
            submitAttempt(a, Instant.now());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your exam time expired and your answers were submitted.");
        }
    }

    private boolean hasAttempts(Long examId) {
        return attempts.existsByExamId(examId);
    }

    private Attempt ownedAttempt(String email, Long id) {
        Student s = student(email);
        return attempts.lockByIdAndStudentId(id, s.id).orElseThrow(() -> notFound("Exam attempt not found."));
    }

    private Student student(String email) {
        return students.findByUserId(user(email).id).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Student profile is not configured."));
    }

    private UserAccount user(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Subject ownedSubject(String email, Long id) {
        return subjects.findById(id).filter(s -> s.teacher != null && s.teacher.user.email.equalsIgnoreCase(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your assigned subjects."));
    }

    private Exam ownedExam(String email, Long id) {
        Exam e = exams.findById(id).orElseThrow(() -> notFound("Exam not found."));
        ownedSubject(email, e.subject.id);
        return e;
    }

    private SubjectView subjectView(Subject s) {
        return new SubjectView(s.id, s.code, s.name, s.branch.name, s.branch.code, s.semester.number, s.description,
                s.teacher == null ? "Unassigned" : s.teacher.fullName, s.teacher == null ? null : s.teacher.id, s.totalMarks, s.passingMarks,
                s.durationMinutes, s.available, exams.countBySubjectId(s.id));
    }

    private ExamView examView(Exam e, Student student) {
        boolean attempted = student != null && attempts.existsByExamIdAndStudentId(e.id, student.id);
        Long attemptId = student == null ? null : attempts.findByStudentIdOrderByStartedAtDesc(student.id).stream()
                .filter(a -> a.exam.id.equals(e.id) && a.submittedAt == null).map(a -> a.id).findFirst().orElse(null);
        String status = Instant.now().isBefore(e.startTime) ? "UPCOMING"
                : Instant.now().isAfter(e.endTime) ? "COMPLETED" : "AVAILABLE";
        return new ExamView(e.id, e.title, e.subject.name, e.subject.code, e.subject.branch.name,
                e.subject.semester.number, e.startTime, e.endTime, e.durationMinutes, e.totalMarks,
                e.passingMarks, status, questions.countByExamId(e.id), e.allowMultipleAttempts, attempted, attemptId);
    }

    private AttemptView attemptView(Attempt a) {
        List<QuestionView> q = questions.findByExamIdOrderById(a.exam.id).stream().map(x -> questionView(x, false)).toList();
        List<AnswerView> saved = answers.findByAttemptId(a.id).stream().sorted(Comparator.comparing(x -> x.question.id))
                .map(x -> new AnswerView(x.question.id, x.selectedOption, x.markedForReview)).toList();
        return new AttemptView(a.id, a.exam.id, a.exam.title, a.exam.subject.name, a.startedAt, a.deadline, Instant.now(), q, saved);
    }

    private QuestionView questionView(Question q, boolean includeCorrect) {
        return new QuestionView(q.id, q.prompt, q.optionA, q.optionB, q.optionC, q.optionD, q.marks,
                includeCorrect ? q.correctOption : null);
    }

    private ResultView resultView(Result r) {
        Attempt a = r.attempt; Exam e = a.exam;
        return new ResultView(r.id, a.id, e.title, e.subject.name, e.subject.code, e.subject.branch.name,
                e.subject.semester.number, a.student.fullName, a.student.usn, a.submittedAt, r.correct,
                r.wrong, r.unanswered, r.obtainedMarks, e.totalMarks, r.percentage, r.passed);
    }

    private void apply(Question q, QuestionRequest input) {
        q.prompt = input.prompt().trim(); q.optionA = input.optionA().trim(); q.optionB = input.optionB().trim();
        q.optionC = input.optionC().trim(); q.optionD = input.optionD().trim(); q.correctOption = input.correctOption();
        q.marks = input.marks();
    }

    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
