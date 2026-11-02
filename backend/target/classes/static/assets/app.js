(() => {
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const workspace = $("#workspace");
  let csrfToken = "";
  let currentUser = null;
  let currentPage = "dashboard";
  let chart = null;
  let activeAttempt = null;
  let currentQuestion = 0;
  let visited = new Set();
  let answers = new Map();
  let saveQueue = Promise.resolve();
  let clock = null;
  let activeQuestions = [];
  let teacherQuestionCache = [];
  let teacherExamCache = [];
  let teacherSubjectsCache = [];
  let adminSubjectCache = [];
  let serverClockOffset = 0;
  let visibleResults = [];
  let toastTimer;

  const esc = value => String(value ?? "").replace(/[&<>"']/g, ch => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[ch]));
  const fmtDate = value => value ? new Date(value).toLocaleString([], { dateStyle: "medium", timeStyle: "short" }) : "—";
  const dateTimeLocal = value => {
    const date = new Date(value);
    date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
    return date.toISOString().slice(0, 16);
  };
  const fmtNumber = value => Number(value || 0).toLocaleString();
  const icon = name => `<i data-lucide="${name}"></i>`;
  const toast = (message, error = false) => {
    const node = $("#toast");
    node.textContent = message;
    node.className = `toast show${error ? " error" : ""}`;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { node.className = "toast"; }, 3200);
  };
  const refreshIcons = () => { if (window.lucide) window.lucide.createIcons(); };

  async function getCsrf() {
    const response = await fetch("/api/auth/csrf", { credentials: "same-origin" });
    const data = await response.json();
    csrfToken = data.token;
  }

  async function api(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    if (method !== "GET" && method !== "HEAD") {
      if (!csrfToken) await getCsrf();
      headers.set("X-XSRF-TOKEN", csrfToken);
    }
    let body = options.body;
    if (body && typeof body === "object" && !(body instanceof FormData) && !(body instanceof URLSearchParams)) {
      headers.set("Content-Type", "application/json");
      body = JSON.stringify(body);
    }
    const response = await fetch(path, { ...options, method, headers, body, credentials: "same-origin" });
    const text = await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = { message: text }; }
    }
    if (!response.ok) {
      if (response.status === 401 && currentUser) signOutView();
      throw new Error(data?.message || `Request failed (${response.status}).`);
    }
    return data;
  }

  async function loadPublicData() {
    try {
      const [stats, branches] = await Promise.all([api("/api/public/stats"), api("/api/public/branches")]);
      $("#stat-branches").textContent = fmtNumber(stats.branches);
      $("#stat-students").textContent = fmtNumber(stats.students);
      $("#stat-subjects").textContent = fmtNumber(stats.subjects);
      $("#stat-exams").textContent = fmtNumber(stats.exams);
      $("#hero-branch-count").textContent = `${stats.branches} engineering branches`;
      $("#register-branch").innerHTML = `<option value="">Select branch</option>${branches.map(b => `<option value="${b.id}">${esc(b.name)}</option>`).join("")}`;
    } catch { toast("Live statistics are unavailable. Check that the backend and MySQL are running.", true); }
  }

  const navByRole = {
    STUDENT: [["dashboard", "layout-dashboard", "Overview"], ["subjects", "book-open", "My subjects"], ["exams", "clipboard-check", "Examinations"], ["results", "chart-no-axes-column-increasing", "My results"], ["profile", "user-round", "My profile"]],
    TEACHER: [["dashboard", "layout-dashboard", "Overview"], ["subjects", "book-open", "My subjects"], ["exams", "clipboard-check", "Manage exams"], ["results", "chart-no-axes-column-increasing", "Result reports"], ["profile", "user-round", "My profile"]],
    ADMIN: [["dashboard", "layout-dashboard", "Overview"], ["branches", "git-branch", "Branches"], ["subjects", "book-open", "Subjects"], ["students", "users-round", "Students"], ["teachers", "presentation", "Teachers"], ["exams", "clipboard-check", "Examinations"], ["results", "chart-no-axes-column-increasing", "Results"], ["profile", "user-round", "My profile"]]
  };

  function showUser(user) {
    currentUser = user;
    $("#public-page").hidden = true;
    $("#auth-layer").hidden = true;
    $("#app-page").hidden = false;
    const name = user.fullName || user.email;
    $("#profile-name").textContent = name;
    $("#profile-role").textContent = user.role;
    $("#avatar-initials").textContent = name.split(/\s+/).slice(0, 2).map(p => p[0]).join("").toUpperCase();
    const links = navByRole[user.role] || navByRole.STUDENT;
    $("#side-navigation").innerHTML = links.map(([page, symbol, label]) =>
      `<button class="side-link${page === currentPage ? " active" : ""}" data-page="${page}">${icon(symbol)}<span>${label}</span></button>`).join("");
    refreshIcons();
    if (location.search.startsWith("?attempt=")) {
      openAttempt(decodeURIComponent(location.search.slice(9))).catch(showError);
    } else {
      navigate(currentPage);
    }
  }

  function signOutView() {
    currentUser = null;
    activeAttempt = null;
    if (clock) clearInterval(clock);
    $("#app-page").hidden = true;
    $("#public-page").hidden = false;
    history.replaceState(null, "", "/");
  }

  async function navigate(page) {
    if (!currentUser) return;
    currentPage = page;
    if (clock) { clearInterval(clock); clock = null; }
    activeAttempt = null;
    history.replaceState(null, "", "/");
    $$(".side-link").forEach(button => button.classList.toggle("active", button.dataset.page === page));
    const titles = { dashboard: "Overview", subjects: "Subjects", exams: currentUser.role === "TEACHER" ? "Manage exams" : "Examinations", results: "Results", branches: "Branches", students: "Students", teachers: "Teachers", profile: "My profile" };
    $("#page-title").textContent = titles[page] || "Overview";
    workspace.innerHTML = `<div class="empty-state">${icon("loader-circle")}<div>Loading your workspace…</div></div>`;
    refreshIcons();
    try {
      if (currentUser.role === "STUDENT") {
        if (page === "dashboard") await renderStudentDashboard();
        else if (page === "subjects") await renderStudentSubjects();
        else if (page === "exams") await renderStudentExams();
        else if (page === "results") await renderResults(await api("/api/student/results"), true);
        else renderProfile();
      } else if (currentUser.role === "TEACHER") {
        if (page === "dashboard") await renderTeacherDashboard();
        else if (page === "subjects") await renderTeacherSubjects();
        else if (page === "exams") await renderTeacherExams();
        else if (page === "results") await renderResults(await api("/api/teacher/results"), false);
        else renderProfile();
      } else {
        if (page === "dashboard") await renderAdminDashboard();
        else if (page === "branches") await renderBranches();
        else if (page === "subjects") await renderAdminSubjects();
        else if (page === "students") await renderPeople("students");
        else if (page === "teachers") await renderPeople("teachers");
        else if (page === "exams") await renderAdminExams();
        else if (page === "results") await renderResults(await api("/api/admin/results"), false);
        else renderProfile();
      }
    } catch (error) { showError(error); }
  }

  function showError(error) {
    toast(error.message || "Something went wrong. Please try again.", true);
    if (workspace && !workspace.innerHTML) workspace.innerHTML = `<div class="empty-state">${esc(error.message)}</div>`;
  }

  function welcome(title, subtitle, action = "") {
    return `<div class="welcome-row"><div><span class="eyebrow">SMARTEXAM WORKSPACE</span><h1>${title}</h1><p>${subtitle}</p></div>${action}</div>`;
  }

  function metric(label, value, symbol, note = "") {
    return `<article class="metric-card"><div class="metric-top"><span>${label}</span><span class="metric-icon">${icon(symbol)}</span></div><strong>${esc(value)}</strong><small>${note}</small></article>`;
  }

  async function renderStudentDashboard() {
    const [data, exams] = await Promise.all([api("/api/student/dashboard"), api("/api/student/exams")]);
    workspace.innerHTML = `${welcome(`Good ${new Date().getHours() < 12 ? "morning" : "day"}, ${esc(data.user.fullName.split(" ")[0])} <span>✦</span>`,
      `${esc(data.user.usn)} · ${esc(data.user.branch)} · Semester ${data.user.semester}`)}
      <div class="dashboard-grid">${metric("MY SUBJECTS", data.subjectCount, "book-open", "In your current semester")}${metric("AVAILABLE EXAMS", data.availableExams, "clipboard-check", "Ready when you are")}${metric("EXAM AVERAGE", `${Number(data.averagePercentage).toFixed(1)}%`, "chart-no-axes-column-increasing", `${data.completedExams} completed`) }${metric("RESULTS · PASS", `${data.passed} / ${data.completedExams}`, "badge-check", `${data.failed} need another look`)}</div>
      <div class="dashboard-content"><section class="panel"><div class="panel-heading"><h2>Performance snapshot</h2><span>From submitted exams</span></div><div class="chart-wrap">${data.recentResults.length ? '<canvas id="performance-chart" aria-label="Recent exam scores"></canvas>' : '<div class="empty-state">Your score trend appears here after your first exam.</div>'}</div></section>
      <section class="panel"><div class="panel-heading"><h2>Up next</h2><span>${exams.filter(e => e.status === "UPCOMING").length} scheduled</span></div>${exams.filter(e => e.status !== "COMPLETED").slice(0, 4).map(e => `<div class="question-row"><div><b>${esc(e.title)}</b><br><small>${esc(e.subject)} · ${fmtDate(e.startTime)}</small></div><span class="status-pill ${e.status === "AVAILABLE" ? "success" : "warning"}">${e.status}</span></div>`).join("") || '<div class="empty-state">No upcoming exams scheduled.</div>'}</section></div>
      <section class="panel" style="margin-top:14px"><div class="panel-heading"><h2>Recent results</h2><button class="btn btn-quiet" data-page="results">View all →</button></div>${resultTable(data.recentResults, true)}</section>`;
    if (data.recentResults.length && window.Chart) {
      if (chart) chart.destroy();
      chart = new Chart($("#performance-chart"), { type: "line", data: { labels: data.recentResults.slice().reverse().map(r => r.subject), datasets: [{ data: data.recentResults.slice().reverse().map(r => r.percentage), borderColor: "#7062dc", backgroundColor: "#eceaff", fill: true, tension: .38, pointRadius: 4, pointBackgroundColor: "#7062dc" }] }, options: { maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { min: 0, max: 100, ticks: { callback: v => `${v}%`, font: { size: 9 } }, grid: { color: "#f0f1f5" } }, x: { ticks: { font: { size: 9 } }, grid: { display: false } } } } });
    }
    refreshIcons();
  }

  async function renderStudentSubjects() {
    const data = await api("/api/student/subjects");
    workspace.innerHTML = `${welcome("My subjects", `${data.length} available subject${data.length === 1 ? "" : "s"} for ${esc(currentUser.branch)} · Semester ${currentUser.semester}`)}
      <div class="dashboard-grid">${metric("SUBJECTS", data.length, "book-open", "Assigned to your semester")}${metric("EXAM READY", data.filter(s => s.examCount > 0).length, "clipboard-check", "With scheduled assessments")}${metric("TOTAL MARKS", data.reduce((n, s) => n + s.totalMarks, 0), "award", "Across available subjects")}${metric("SEMESTER", currentUser.semester, "calendar-days", esc(currentUser.branch))}</div>
      <div class="toolbar"><div><h2>Subject directory</h2><p>Configured by your department administrator.</p></div><input class="filter-input" data-filter="subject-table" placeholder="Search subjects…" aria-label="Search subjects"></div>
      ${data.length ? `<div class="data-table-wrap"><table class="data-table" id="subject-table"><thead><tr><th>Subject</th><th>Branch / semester</th><th>Teacher</th><th>Assessment</th><th>Marks · pass</th></tr></thead><tbody>${data.map(s => `<tr><td><strong>${esc(s.name)}</strong><br>${esc(s.code)}</td><td>${esc(s.branchCode)} · Sem ${s.semester}</td><td>${esc(s.teacher)}</td><td>${s.examCount} exam${s.examCount === 1 ? "" : "s"}</td><td>${s.totalMarks} · ${s.passingMarks}</td></tr>`).join("")}</tbody></table></div>` : '<div class="panel empty-state">No subjects have been configured for your semester yet.</div>'}`;
    refreshIcons();
  }

  async function renderStudentExams() {
    const data = await api("/api/student/exams");
    const cards = data.map(e => {
      const open = e.attemptId != null;
      const available = e.status === "AVAILABLE";
      let action = available ? `<button class="btn btn-primary" data-action="${open ? "resume-exam" : "start-exam"}" data-id="${e.id}" ${open ? `data-attempt="${e.attemptId}"` : ""}>${open ? "Resume exam" : "Read instructions & start"} ${icon("arrow-right")}</button>` : `<button class="btn btn-outline" disabled>${e.status === "UPCOMING" ? "Not open yet" : (e.attempted ? "Attempt submitted" : "Exam closed")}</button>`;
      return `<article class="exam-card"><div class="exam-card-top"><span class="exam-subject-icon">${icon("book-check")}</span><span class="status-pill ${available ? "success" : e.status === "UPCOMING" ? "warning" : ""}">${e.status}</span></div><h3>${esc(e.title)}</h3><p>${esc(e.subject)} · ${esc(e.subjectCode)}</p><div class="exam-meta"><span>STARTS<b>${fmtDate(e.startTime)}</b></span><span>DURATION<b>${e.durationMinutes} minutes</b></span><span>QUESTIONS<b>${e.questionCount}</b></span><span>PASS MARK<b>${e.passingMarks} / ${e.totalMarks}</b></span></div>${action}</article>`;
    }).join("");
    workspace.innerHTML = `${welcome("Examinations", "Review the exam details before you begin. Your timer starts on the server when your attempt is created.")}<div class="toolbar"><div><h2>Exams for your semester</h2><p>${data.length} examination${data.length === 1 ? "" : "s"} · ${esc(currentUser.branch)} · Semester ${currentUser.semester}</p></div></div>${cards ? `<div class="exam-grid">${cards}</div>` : '<div class="panel empty-state">No examinations have been scheduled for your semester.</div>'}`;
    refreshIcons();
  }

  function resultTable(items, student) {
    if (!items.length) return '<div class="empty-state">Results will appear after an exam is submitted.</div>';
    return `<div class="data-table-wrap"><table class="data-table"><thead><tr>${student ? "" : "<th>Student</th>"}<th>Examination</th><th>Subject</th><th>Score</th><th>Percentage</th><th>Status</th><th>Submitted</th></tr></thead><tbody>${items.map(r => `<tr>${student ? "" : `<td><strong>${esc(r.student)}</strong><br>${esc(r.usn)}</td>`}<td><strong>${esc(r.exam)}</strong></td><td>${esc(r.subject)}<br>${esc(r.subjectCode)}</td><td>${r.obtainedMarks} / ${r.totalMarks}</td><td>${Number(r.percentage).toFixed(1)}%</td><td><span class="status-pill ${r.passed ? "success" : "danger"}">${r.passed ? "PASS" : "NOT PASSED"}</span></td><td>${fmtDate(r.submittedAt)}</td></tr>`).join("")}</tbody></table></div>`;
  }

  async function renderResults(items, student) {
    visibleResults = items;
    const title = student ? "My results" : "Result reports";
    workspace.innerHTML = `${welcome(title, student ? "Your submitted exam results, evaluated securely by SMARTEXAM." : "Exam performance, calculated and recorded by the server.", `<button class="btn btn-outline" data-action="download-results">${icon("download")} Download CSV</button>`)}
      <div class="dashboard-grid">${metric("RESULTS", items.length, "file-check-2", "Submitted exams")}${metric("PASSED", items.filter(r => r.passed).length, "badge-check", "At or above pass marks")}${metric("NOT PASSED", items.filter(r => !r.passed).length, "rotate-ccw", "Review your progress")}${metric("AVERAGE", `${items.length ? (items.reduce((sum, r) => sum + r.percentage, 0) / items.length).toFixed(1) : "0.0"}%`, "chart-no-axes-column-increasing", "Across submitted exams")}</div>
      <div class="toolbar"><div><h2>Examination results</h2><p>${items.length} recorded result${items.length === 1 ? "" : "s"}</p></div><input class="filter-input" data-filter="results-table" placeholder="Search results…"></div>${resultTable(items, student)}`;
    refreshIcons();
  }

  async function renderTeacherDashboard() {
    const [subjects, exams, results] = await Promise.all([api("/api/teacher/subjects"), api("/api/teacher/exams"), api("/api/teacher/results")]);
    workspace.innerHTML = `${welcome(`Welcome, ${esc(currentUser.fullName)}`, "Your assigned subjects and examination activity, all in one place.", `<button class="btn btn-primary" data-page="exams">${icon("plus")} Create examination</button>`)}
      <div class="dashboard-grid">${metric("ASSIGNED SUBJECTS", subjects.length, "book-open", "Subjects you manage")}${metric("EXAMINATIONS", exams.length, "clipboard-check", "Across assigned subjects")}${metric("RESULTS", results.length, "file-check-2", "Submissions from your students")}${metric("PASS RATE", `${results.length ? Math.round(results.filter(r => r.passed).length * 100 / results.length) : 0}%`, "chart-no-axes-column-increasing", "Calculated from results")}</div>
      <section class="panel" style="margin-top:15px"><div class="panel-heading"><h2>Recent examinations</h2><button class="btn btn-quiet" data-page="exams">Manage exams →</button></div>${exams.length ? `<div class="data-table-wrap"><table class="data-table"><thead><tr><th>Examination</th><th>Subject</th><th>Schedule</th><th>Questions</th><th>Status</th></tr></thead><tbody>${exams.slice(0, 6).map(e => `<tr><td><strong>${esc(e.title)}</strong></td><td>${esc(e.subject)}</td><td>${fmtDate(e.startTime)}</td><td>${e.questionCount}</td><td><span class="status-pill">${e.status}</span></td></tr>`).join("")}</tbody></table></div>` : '<div class="empty-state">Create your first examination to get started.</div>'}</section>`;
    refreshIcons();
  }

  async function renderTeacherSubjects() {
    const data = await api("/api/teacher/subjects");
    workspace.innerHTML = `${welcome("My subjects", "Only the subjects assigned to your teacher account are shown here.")}<div class="toolbar"><div><h2>Assigned subjects</h2><p>${data.length} subject${data.length === 1 ? "" : "s"}</p></div></div>${data.length ? `<div class="data-table-wrap"><table class="data-table"><thead><tr><th>Subject</th><th>Branch / semester</th><th>Marks</th><th>Duration</th><th>Exams</th></tr></thead><tbody>${data.map(s => `<tr><td><strong>${esc(s.name)}</strong><br>${esc(s.code)}</td><td>${esc(s.branchCode)} · Semester ${s.semester}</td><td>${s.totalMarks} · pass ${s.passingMarks}</td><td>${s.durationMinutes} min</td><td>${s.examCount}</td></tr>`).join("")}</tbody></table></div>` : '<div class="panel empty-state">An administrator must assign a subject to your account before you can create exams.</div>'}`;
  }

  async function renderTeacherExams() {
    const [exams, subjects] = await Promise.all([api("/api/teacher/exams"), api("/api/teacher/subjects")]);
    teacherExamCache = exams;
    teacherSubjectsCache = subjects;
    workspace.innerHTML = `${welcome("Manage examinations", "Create exams for your assigned subjects, then build a four-option MCQ question bank.")}<section class="panel form-panel"><div class="panel-heading"><h2 id="exam-form-title">Create an examination</h2><span>All times use your browser's local timezone</span></div>${subjects.length ? `<form id="exam-create-form" class="form-stack">
      <div class="field-pair"><label>Exam name<input name="title" placeholder="e.g. Internal Assessment 1" required></label><label>Assigned subject<select name="subjectId" required>${subjects.map(s => `<option value="${s.id}">${esc(s.code)} · ${esc(s.name)}</option>`).join("")}</select></label></div>
      <div class="field-pair"><label>Starts<input name="startTime" type="datetime-local" required></label><label>Ends<input name="endTime" type="datetime-local" required></label></div>
      <div class="field-pair"><label>Duration (minutes)<input name="durationMinutes" type="number" min="1" value="${subjects[0].durationMinutes}" required></label><label>Total marks<input name="totalMarks" type="number" min="1" value="${subjects[0].totalMarks}" required></label></div>
      <div class="field-pair"><label>Passing marks<input name="passingMarks" type="number" min="1" value="${subjects[0].passingMarks}" required></label><span class="form-note">Additional attempts require explicit administrator approval.</span></div>
      <button class="btn btn-primary" type="submit">Create examination ${icon("arrow-right")}</button></form>` : '<div class="empty-state">No subjects have been assigned to your teacher account.</div>'}</section>
      <div class="toolbar"><div><h2>Your examinations</h2><p>${exams.length} exam${exams.length === 1 ? "" : "s"} · Add questions before students begin.</p></div><input class="filter-input" data-filter="teacher-exams" placeholder="Search exams…"></div>
      ${exams.length ? `<div class="data-table-wrap"><table class="data-table" id="teacher-exams"><thead><tr><th>Exam</th><th>Subject</th><th>Schedule</th><th>Questions / marks</th><th>Actions</th></tr></thead><tbody>${exams.map(e => `<tr><td><strong>${esc(e.title)}</strong><br><span class="status-pill">${e.status}</span></td><td>${esc(e.subject)}<br>${esc(e.subjectCode)}</td><td>${fmtDate(e.startTime)}<br>${e.durationMinutes} min</td><td>${e.questionCount} questions · ${e.totalMarks} marks</td><td><button class="btn btn-outline" data-action="manage-questions" data-id="${e.id}">Questions</button> <button class="btn btn-outline" data-action="edit-exam" data-id="${e.id}">Edit</button> <button class="btn btn-outline" data-action="delete-exam" data-id="${e.id}">Delete</button></td></tr>`).join("")}</tbody></table></div>` : '<div class="panel empty-state">No exams yet. Create one above to get started.</div>'}`;
    refreshIcons();
  }

  async function showQuestionManager(examId) {
    const [exam, allExams] = await Promise.all([api(`/api/teacher/exams/${examId}/questions`), api("/api/teacher/exams")]);
    const target = allExams.find(e => e.id === examId);
    teacherQuestionCache = exam;
    currentPage = "exams";
    $("#page-title").textContent = "Question bank";
    workspace.innerHTML = `${welcome("Question bank", `${esc(target?.title || "Examination")} · ${esc(target?.subject || "")} · ${target?.totalMarks ?? ""} marks`)}<button class="btn btn-quiet" data-page="exams">← Back to examinations</button>
      <section class="panel form-panel question-editor"><div class="panel-heading"><h2 id="question-form-title">Add a question</h2><span>Single correct answer · Four options</span></div>
      <form id="question-form" data-exam-id="${examId}" class="form-stack"><label>Question prompt<textarea name="prompt" required maxlength="2000"></textarea></label>
      <div class="inline-options"><label>Option A<input name="optionA" required></label><label>Option B<input name="optionB" required></label><label>Option C<input name="optionC" required></label><label>Option D<input name="optionD" required></label></div>
      <div class="field-pair"><label>Correct option<select name="correctOption" required><option value="">Select correct option</option><option>A</option><option>B</option><option>C</option><option>D</option></select></label><label>Marks<input name="marks" type="number" min="1" value="1" required></label></div>
      <div class="toolbar-actions"><button class="btn btn-primary" type="submit">Save question</button><button class="btn btn-outline" type="button" data-action="cancel-question-edit" hidden>Cancel edit</button></div></form></section>
      <section class="panel" style="margin-top:15px"><div class="panel-heading"><h2>Questions (${exam.length})</h2><span>Correct answers are visible only to teachers.</span></div>${exam.length ? exam.map((q, i) => `<div class="question-row"><div><b>${i + 1}. ${esc(q.prompt)}</b><br><small>Correct: ${esc(q.correctOption)} · ${q.marks} mark${q.marks === 1 ? "" : "s"}</small></div><div class="question-actions"><button aria-label="Edit question" data-action="edit-question" data-id="${q.id}">${icon("pencil")}</button><button aria-label="Delete question" data-action="delete-question" data-id="${q.id}" data-exam="${examId}">${icon("trash-2")}</button></div></div>`).join("") : '<div class="empty-state">No questions yet. Add the first question above.</div>'}</section>`;
    $("#question-form").dataset.examId = examId;
    refreshIcons();
  }

  async function renderAdminDashboard() {
    const [s, branches, exams] = await Promise.all([api("/api/admin/summary"), api("/api/admin/branches"), api("/api/admin/exams")]);
    workspace.innerHTML = `${welcome("Administration overview", "A database-backed snapshot of your academic examination system.")}<div class="dashboard-grid">${metric("STUDENTS", s.students, "users-round", "Registered learner accounts")}${metric("TEACHERS", s.teachers, "presentation", "Teaching accounts")}${metric("SUBJECTS", s.subjects, "book-open", "Configured across semesters")}${metric("EXAMINATIONS", s.exams, "clipboard-check", `${s.results} recorded results`)}</div>
      <div class="dashboard-content"><section class="panel"><div class="panel-heading"><h2>Academic branches</h2><button class="btn btn-quiet" data-page="branches">Configure →</button></div><div class="branch-pills">${branches.map(b => `<span>${esc(b.code)}</span>`).join("")}</div><p style="font-size:10px;color:#8b93a5;line-height:1.7">${branches.length} branches · semesters 1–8 are configured for each branch.</p></section>
      <section class="panel"><div class="panel-heading"><h2>Upcoming examinations</h2><span>${exams.filter(e => e.status === "UPCOMING").length}</span></div>${exams.filter(e => e.status === "UPCOMING").slice(0, 4).map(e => `<div class="question-row"><div><b>${esc(e.title)}</b><br><small>${esc(e.subject)} · ${fmtDate(e.startTime)}</small></div><span class="status-pill warning">UPCOMING</span></div>`).join("") || '<div class="empty-state">No upcoming exams.</div>'}</section></div>
      <div class="toolbar"><div><h2>Quick actions</h2><p>Manage the academic calendar and campus accounts.</p></div></div><div class="toolbar-actions"><button class="btn btn-outline" data-page="subjects">${icon("book-open")} Manage subjects</button><button class="btn btn-outline" data-page="teachers">${icon("user-plus")} Add teacher</button><button class="btn btn-outline" data-page="students">${icon("users-round")} Browse students</button></div>`;
    refreshIcons();
  }

  async function renderBranches(branchId) {
    const data = await api("/api/admin/branches");
    const selected = Number(branchId || data[0]?.id);
    const semesters = selected ? await api(`/api/admin/branches/${selected}/semesters`) : [];
    workspace.innerHTML = `${welcome("Academic branches", "Configure departments. Each branch has database-backed semester subject and examination counts.")}<section class="panel form-panel"><div class="panel-heading"><h2>Add a branch</h2><span>Branch code must be unique</span></div><form id="branch-form" class="form-stack"><div class="field-pair"><label>Branch code<input name="code" maxlength="12" placeholder="e.g. ROB" required></label><label>Branch name<input name="name" placeholder="Department name" required></label></div><button class="btn btn-primary">Add branch ${icon("plus")}</button></form></section>
      <div class="toolbar"><div><h2>Semester overview</h2><p>Select a department to review all eight semesters and their assessment status.</p></div><select class="filter-input" data-semester-branch aria-label="Select branch">${data.map(b => `<option value="${b.id}" ${selected === b.id ? "selected" : ""}>${esc(b.code)} · ${esc(b.name)}</option>`).join("")}</select></div>
      <div class="data-table-wrap"><table class="data-table"><thead><tr><th>Semester</th><th>Subjects</th><th>Total exams</th><th>Upcoming</th><th>Available now</th><th>Closed</th></tr></thead><tbody>${semesters.map(s => `<tr><td><strong>Semester ${s.number}</strong></td><td>${s.subjectCount}</td><td>${s.examCount}</td><td>${s.upcomingExams}</td><td><span class="status-pill ${s.availableExams ? "success" : ""}">${s.availableExams}</span></td><td>${s.completedExams}</td></tr>`).join("")}</tbody></table></div>
      <div class="toolbar"><div><h2>Configured branches</h2><p>${data.length} branch${data.length === 1 ? "" : "es"} · 8 semester records each</p></div></div><div class="branch-pills">${data.map(b => `<span>${esc(b.code)}</span>`).join("")}</div>`;
    refreshIcons();
  }

  async function renderAdminSubjects() {
    const [data, branches, teachers] = await Promise.all([api("/api/admin/subjects"), api("/api/admin/branches"), api("/api/admin/teachers")]);
    adminSubjectCache = data;
    const formFields = `<div class="field-pair"><label>Subject code<input name="code" required></label><label>Subject name<input name="name" required></label></div><div class="field-pair"><label>Branch<select name="branchId" required>${branches.map(b => `<option value="${b.id}">${esc(b.code)} · ${esc(b.name)}</option>`).join("")}</select></label><label>Semester<select name="semester">${Array.from({ length: 8 }, (_, i) => `<option>${i + 1}</option>`).join("")}</select></label></div><label>Description<textarea name="description"></textarea></label><div class="field-pair"><label>Assigned teacher<select name="teacherId"><option value="">Unassigned</option>${teachers.map(t => `<option value="${t.id}">${esc(t.fullName)} · ${esc(t.email)}</option>`).join("")}</select></label><label>Total marks<input name="totalMarks" type="number" min="1" value="100" required></label></div><div class="field-pair"><label>Passing marks<input name="passingMarks" type="number" min="1" value="40" required></label><label>Exam duration (minutes)<input name="durationMinutes" type="number" min="1" value="60" required></label></div><label class="checkbox-label"><span>Available to students</span><input name="available" type="checkbox" checked></label>`;
    workspace.innerHTML = `${welcome("Subject catalogue", "Configure syllabus by branch and semester, and assign each subject to its teacher.")}<section class="panel form-panel"><div class="panel-heading"><h2 id="subject-form-title">Add a subject</h2><span>Subjects are database-driven</span></div><form id="subject-form" class="form-stack">${formFields}<div class="toolbar-actions"><button class="btn btn-primary" type="submit">Save subject ${icon("plus")}</button><button class="btn btn-outline" type="button" data-action="cancel-subject-edit" hidden>Cancel edit</button></div></form></section><div class="toolbar"><div><h2>All subjects</h2><p>${data.length} configured subjects</p></div><input class="filter-input" data-filter="admin-subjects" placeholder="Search subjects…"></div>
      ${data.length ? `<div class="data-table-wrap"><table class="data-table" id="admin-subjects"><thead><tr><th>Subject</th><th>Branch / semester</th><th>Teacher</th><th>Marks · exams</th><th>Availability</th><th>Actions</th></tr></thead><tbody>${data.map(s => `<tr><td><strong>${esc(s.name)}</strong><br>${esc(s.code)}</td><td>${esc(s.branchCode)} · ${s.semester}</td><td>${esc(s.teacher)}</td><td>${s.totalMarks} / ${s.passingMarks} · ${s.examCount}</td><td><span class="status-pill ${s.available ? "success" : "warning"}">${s.available ? "AVAILABLE" : "HIDDEN"}</span></td><td><button class="btn btn-outline" data-action="edit-subject" data-id="${s.id}">Edit</button> <button class="btn btn-outline" data-action="delete-subject" data-id="${s.id}">Delete</button></td></tr>`).join("")}</tbody></table></div>` : '<div class="panel empty-state">No subjects configured yet.</div>'}`;
    refreshIcons();
  }

  async function renderPeople(type) {
    const isTeacher = type === "teachers";
    const data = await api(`/api/admin/${type}`);
    workspace.innerHTML = `${welcome(isTeacher ? "Teacher accounts" : "Student directory", isTeacher ? "Create teacher logins, then assign subjects from the subject catalogue." : "Student records are scoped to their registered branch and semester.", isTeacher ? `<button class="btn btn-primary" data-action="open-teacher-form">${icon("user-plus")} Add teacher</button>` : "")}
      ${isTeacher ? `<section id="teacher-create-panel" class="panel form-panel" hidden><div class="panel-heading"><h2>Provision a teacher</h2><button class="icon-btn" data-action="close-teacher-form">${icon("x")}</button></div><form id="teacher-form" class="form-stack"><div class="field-pair"><label>Full name<input name="fullName" required></label><label>Email<input name="email" type="email" required></label></div><div class="field-pair"><label>Phone<input name="phone" required></label><label>Temporary password<input name="password" type="password" minlength="8" required></label></div><button class="btn btn-primary">Create teacher account</button></form></section>` : ""}
      <div class="toolbar"><div><h2>${isTeacher ? "Registered teachers" : "Registered students"}</h2><p>${data.length} account${data.length === 1 ? "" : "s"}</p></div><input class="filter-input" data-filter="people-table" placeholder="Search by name, ID, or email…"></div>
      ${data.length ? `<div class="data-table-wrap"><table class="data-table" id="people-table"><thead><tr><th>Name / ID</th><th>Email</th>${isTeacher ? "<th>Role</th>" : "<th>Branch</th><th>Semester</th>"}</tr></thead><tbody>${data.map(p => `<tr><td><strong>${esc(p.fullName)}</strong>${p.usn ? `<br>${esc(p.usn)}` : ""}</td><td>${esc(p.email)}</td>${isTeacher ? "<td>TEACHER</td>" : `<td>${esc(p.branch)}</td><td>${p.semester}</td>`}</tr>`).join("")}</tbody></table></div>` : `<div class="panel empty-state">No ${isTeacher ? "teachers" : "students"} registered yet.</div>`}`;
    refreshIcons();
  }

  async function renderAdminExams() {
    const [exams, branches] = await Promise.all([api("/api/admin/exams"), api("/api/admin/branches")]);
    workspace.innerHTML = `${welcome("Examinations", "Search and review scheduled examinations across the institution.")}<div class="toolbar"><div><h2>Examination schedule</h2><p>${exams.length} total examinations · ${branches.length} branches</p></div><input class="filter-input" data-filter="admin-exams" placeholder="Search by exam or subject…"></div>
      ${exams.length ? `<div class="data-table-wrap"><table class="data-table" id="admin-exams"><thead><tr><th>Exam</th><th>Subject / branch</th><th>Schedule</th><th>Questions</th><th>Status</th><th>Additional attempt</th></tr></thead><tbody>${exams.map(e => `<tr><td><strong>${esc(e.title)}</strong></td><td>${esc(e.subject)}<br>${esc(e.branch)} · Sem ${e.semester}</td><td>${fmtDate(e.startTime)}<br>Ends ${fmtDate(e.endTime)}</td><td>${e.questionCount}</td><td><span class="status-pill">${esc(e.status)}</span></td><td><button class="btn btn-outline" data-action="toggle-additional-attempt" data-id="${e.id}" data-enabled="${!e.allowMultipleAttempts}">${e.allowMultipleAttempts ? "Disable" : "Enable one"}</button></td></tr>`).join("")}</tbody></table></div>` : '<div class="panel empty-state">No examinations created yet.</div>'}`;
  }

  function renderProfile() {
    const u = currentUser;
    workspace.innerHTML = `${welcome("My profile", "Your account identity and academic access.")}<section class="panel profile-card"><div class="panel-heading"><h2>Account details</h2><span class="status-pill success">ACTIVE</span></div><div class="profile-fields"><div><span>Full name</span><b>${esc(u.fullName)}</b></div><div><span>Role</span><b>${esc(u.role)}</b></div><div><span>Email address</span><b>${esc(u.email)}</b></div>${u.usn ? `<div><span>USN / student ID</span><b>${esc(u.usn)}</b></div><div><span>Branch</span><b>${esc(u.branch)}</b></div><div><span>Semester</span><b>${u.semester}</b></div>` : ""}</div></section>`;
  }

  async function openAttempt(id) {
    if (clock) clearInterval(clock);
    const data = await api(`/api/student/attempts/${id}`);
    activeAttempt = data;
    serverClockOffset = new Date(data.serverNow).getTime() - Date.now();
    activeQuestions = data.questions;
    answers = new Map(data.answers.map(a => [a.questionId, { selectedOption: a.selectedOption, markedForReview: a.markedForReview }]));
    currentQuestion = 0;
    visited = new Set([activeQuestions[0]?.id].filter(Boolean));
    currentPage = "exams";
    history.replaceState(null, "", `/?attempt=${encodeURIComponent(id)}`);
    renderAttempt();
  }

  async function startExam(examId) {
    const all = await api("/api/student/exams");
    const exam = all.find(e => e.id === Number(examId));
    if (!exam) throw new Error("Exam not found.");
    const instructions = `Before you begin:\n\n• ${exam.title} · ${exam.subject} (${exam.subjectCode})\n• ${exam.durationMinutes} minutes · ${exam.questionCount} questions\n• ${exam.totalMarks} total marks · ${exam.passingMarks} passing marks\n• Your server-recorded timer starts as soon as you continue.\n• Your saved answers remain available if you refresh this page.\n• Once the deadline passes, your attempt is submitted automatically.\n\nStart now?`;
    if (!window.confirm(instructions)) return;
    const attempt = await api(`/api/student/exams/${examId}/start`, { method: "POST" });
    await openAttempt(attempt.id);
  }

  function renderAttempt() {
    const q = activeQuestions[currentQuestion];
    if (!q) { workspace.innerHTML = '<div class="panel empty-state">This examination contains no questions.</div>'; return; }
    visited.add(q.id);
    const answer = answers.get(q.id) || { selectedOption: null, markedForReview: false };
    const choice = (letter, text) => `<label class="answer-option${answer.selectedOption === letter ? " selected" : ""}"><input type="radio" name="exam-answer" value="${letter}" ${answer.selectedOption === letter ? "checked" : ""}><b>${letter}.</b> ${esc(text)}</label>`;
    workspace.innerHTML = `<div class="exam-screen"><div class="exam-screen-header"><div><h1>${esc(activeAttempt.title)}</h1><p>${esc(activeAttempt.subject)} · ${activeQuestions.length} questions · Your progress saves automatically</p></div><div id="exam-timer" class="timer">${icon("timer")} <span>--:--</span></div></div>
      <div class="exam-layout"><section class="question-card"><span class="question-number">QUESTION ${currentQuestion + 1} OF ${activeQuestions.length} · ${q.marks} MARK${q.marks === 1 ? "" : "S"}</span><h2>${esc(q.prompt)}</h2><div class="option-list">${choice("A", q.optionA)}${choice("B", q.optionB)}${choice("C", q.optionC)}${choice("D", q.optionD)}</div>
      <div class="question-controls"><button class="btn btn-outline" data-action="previous-question" ${currentQuestion === 0 ? "disabled" : ""}>${icon("arrow-left")} Previous</button><button class="btn btn-outline" data-action="toggle-review">${answer.markedForReview ? icon("bookmark-check") : icon("bookmark")} ${answer.markedForReview ? "Remove review mark" : "Mark for review"}</button>${currentQuestion < activeQuestions.length - 1 ? `<button class="btn btn-primary" data-action="next-question">Next ${icon("arrow-right")}</button>` : `<button class="btn btn-primary" data-action="confirm-submit">${icon("send")} Submit exam</button>`}</div></section>
      <aside class="question-side"><h3>Question navigator</h3><p>Choose any question. Your answers are saved as you go.</p><div class="question-map">${activeQuestions.map((item, index) => {
        const state = answers.get(item.id) || {};
          return `<button data-action="jump-question" data-index="${index}" class="${index === currentQuestion ? "current " : ""}${state.selectedOption ? "answered " : ""}${state.markedForReview ? "review " : ""}${!visited.has(item.id) ? "not-visited" : ""}" aria-label="Question ${index + 1}${state.selectedOption ? ", answered" : ", unanswered"}${state.markedForReview ? ", marked for review" : ""}${!visited.has(item.id) ? ", not visited" : ""}">${index + 1}</button>`;
      }).join("")}</div>      <div class="legend"><span><i></i> Answered</span><span><i></i> Marked for review</span><span><i></i> Unanswered</span><span><i></i> Not visited</span></div><button class="btn btn-outline btn-block" data-action="confirm-submit">Review & submit</button></aside></div></div>`;
    $("#page-title").textContent = "In progress";
    refreshIcons();
    updateTimer();
    if (!clock) clock = setInterval(updateTimer, 1000);
  }

  function updateTimer() {
    if (!activeAttempt) return;
    const remaining = Math.max(0, new Date(activeAttempt.deadline).getTime() - (Date.now() + serverClockOffset));
    const minutes = Math.floor(remaining / 60000);
    const seconds = Math.floor((remaining % 60000) / 1000);
    const timer = $("#exam-timer");
    if (timer) {
      timer.querySelector("span").textContent = `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
      timer.classList.toggle("expiring", remaining < 5 * 60 * 1000);
    }
    if (remaining === 0) submitAttempt(true).catch(showError);
  }

  function queueAnswer(questionId) {
    const value = answers.get(questionId) || { selectedOption: null, markedForReview: false };
    const request = saveQueue.catch(() => {}).then(() => api(`/api/student/attempts/${activeAttempt.id}/answers`, {
      method: "PUT", body: { questionId, selectedOption: value.selectedOption || null, markedForReview: value.markedForReview }
    }));
    saveQueue = request;
    request.catch(showError);
    return request;
  }

  async function submitAttempt(expired = false) {
    if (!activeAttempt) return;
    if (clock) { clearInterval(clock); clock = null; }
    await saveQueue;
    const result = await api(`/api/student/attempts/${activeAttempt.id}/submit`, { method: "POST" });
    activeAttempt = null;
    history.replaceState(null, "", "/");
    renderResultCard(result, expired);
  }

  function renderResultCard(r, expired = false) {
    currentPage = "results";
    $("#page-title").textContent = "Exam result";
    const correct = r.correct;
    const wrong = r.wrong;
    const unanswered = r.unanswered;
    workspace.innerHTML = `<section class="panel" style="max-width:760px;margin:12px auto;text-align:center;padding:35px"><span class="feature-symbol ${r.passed ? "purple" : "coral"}" style="margin:0 auto 15px">${icon(r.passed ? "badge-check" : "book-open-check")}</span><span class="eyebrow" style="justify-content:center">${expired ? "TIME EXPIRED · ATTEMPT SUBMITTED" : "EXAM SUBMITTED"}</span><h1 style="font:800 25px Manrope;margin:13px 0 7px">${r.passed ? "Well done — you passed." : "Your result is ready."}</h1><p style="font-size:11px;color:#8991a4">${esc(r.exam)} · ${esc(r.subject)}</p><div class="dashboard-grid" style="text-align:left;margin:24px 0">${metric("OBTAINED", `${r.obtainedMarks} / ${r.totalMarks}`, "award", `${Number(r.percentage).toFixed(1)}%`) }${metric("CORRECT", correct, "circle-check", "Correct answers")}${metric("INCORRECT", wrong, "circle-x", "Wrong answers")}${metric("UNANSWERED", unanswered, "circle-help", r.passed ? "PASS" : "Passing mark not reached")}</div><button class="btn btn-primary" data-page="results">View all results ${icon("arrow-right")}</button></section>`;
    refreshIcons();
  }

  function downloadCsv() {
    if (!visibleResults.length) { toast("There are no results to download.", true); return; }
    const columns = ["Student", "USN", "Exam", "Subject", "Branch", "Semester", "Obtained", "Total", "Percentage", "Pass", "Correct", "Wrong", "Unanswered", "Submitted"];
    const rows = visibleResults.map(r => [r.student, r.usn, r.exam, r.subject, r.branch, r.semester, r.obtainedMarks, r.totalMarks, Number(r.percentage).toFixed(2), r.passed ? "PASS" : "NOT PASSED", r.correct, r.wrong, r.unanswered, r.submittedAt]);
    const csv = [columns, ...rows].map(row => row.map(x => `"${String(x ?? "").replace(/"/g, '""')}"`).join(",")).join("\r\n");
    const link = document.createElement("a");
    link.href = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
    link.download = "smartexam-results.csv";
    link.click();
    URL.revokeObjectURL(link.href);
  }

  async function signOut() {
    await api("/api/auth/logout", { method: "POST" });
    signOutView();
    toast("You have signed out.");
  }

  function setAuthMode(mode) {
    const register = mode === "register";
    $("#login-form").hidden = register;
    $("#register-form").hidden = !register;
    $$(".auth-tab").forEach(tab => tab.classList.toggle("active", tab.dataset.action === `show-${mode}`));
    $("#auth-title").textContent = register ? "Create your account" : "Welcome back";
    $("#auth-copy").textContent = register ? "Your student profile starts here." : "Sign in to pick up where you left off.";
  }

  async function onAuthSubmit(event) {
    event.preventDefault();
    const form = event.target;
    const button = $("button[type=submit]", form);
    button.disabled = true;
    try {
      if (form.id === "register-form") {
        const values = Object.fromEntries(new FormData(form));
        values.semester = Number(values.semester);
        if (values.password !== values.confirmPassword) throw new Error("Passwords do not match.");
        await api("/api/auth/register", { method: "POST", body: values });
        form.reset();
        $("#login-form [name=email]").value = values.email;
        $("#login-form [name=password]").value = values.password;
        $("#login-form").requestSubmit();
        return;
      }
      await getCsrf();
      const values = new URLSearchParams(new FormData(form));
      const response = await fetch("/api/auth/login", { method: "POST", body: values, headers: { "X-XSRF-TOKEN": csrfToken }, credentials: "same-origin" });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "Sign in failed.");
      await getCsrf();
      const me = await api("/api/auth/me");
      currentPage = "dashboard";
      showUser(me);
    } catch (error) { toast(error.message, true); }
    finally { button.disabled = false; }
  }

  async function onWorkspaceSubmit(event) {
    const form = event.target;
    if (!form.matches("form")) return;
    event.preventDefault();
    const button = $("button[type=submit]", form) || form.querySelector("button");
    if (button) button.disabled = true;
    try {
      const values = Object.fromEntries(new FormData(form));
      if (form.id === "exam-create-form") {
        values.subjectId = Number(values.subjectId);
        values.durationMinutes = Number(values.durationMinutes);
        values.totalMarks = Number(values.totalMarks);
        values.passingMarks = Number(values.passingMarks);
        values.allowMultipleAttempts = false;
        values.startTime = new Date(values.startTime).toISOString();
        values.endTime = new Date(values.endTime).toISOString();
        const examId = form.dataset.examId;
        await api(`/api/teacher/exams${examId ? `/${examId}` : ""}`, { method: examId ? "PUT" : "POST", body: values });
        toast(examId ? "Examination updated." : "Examination created. Add questions before its start time.");
        delete form.dataset.examId;
        $("#exam-form-title").textContent = "Create an examination";
        $("button[type=submit]", form).textContent = "Create examination";
        form.reset();
        await renderTeacherExams();
      } else if (form.id === "question-form") {
        const examId = form.dataset.examId;
        const questionId = form.dataset.questionId;
        values.marks = Number(values.marks);
        await api(`/api/teacher/exams/${examId}/questions${questionId ? `/${questionId}` : ""}`, { method: questionId ? "PUT" : "POST", body: values });
        toast(questionId ? "Question updated." : "Question added.");
        await showQuestionManager(Number(examId));
      } else if (form.id === "branch-form") {
        await api("/api/admin/branches", { method: "POST", body: values });
        toast("Branch and its eight semesters created.");
        await renderBranches();
        await loadPublicData();
      } else if (form.id === "subject-form") {
        values.branchId = Number(values.branchId);
        values.semester = Number(values.semester);
        values.teacherId = values.teacherId ? Number(values.teacherId) : null;
        values.totalMarks = Number(values.totalMarks);
        values.passingMarks = Number(values.passingMarks);
        values.durationMinutes = Number(values.durationMinutes);
        values.available = form.elements.available.checked;
        const subjectId = form.dataset.subjectId;
        await api(`/api/admin/subjects${subjectId ? `/${subjectId}` : ""}`, { method: subjectId ? "PUT" : "POST", body: values });
        toast(subjectId ? "Subject updated." : "Subject configured.");
        await renderAdminSubjects();
        await loadPublicData();
      } else if (form.id === "teacher-form") {
        await api("/api/admin/teachers", { method: "POST", body: values });
        toast("Teacher account created. Assign subjects in the subject catalogue.");
        await renderPeople("teachers");
      }
    } catch (error) { showError(error); }
    finally { if (button?.isConnected) button.disabled = false; }
  }

  document.addEventListener("click", async event => {
    const pageButton = event.target.closest("[data-page]");
    if (pageButton && currentUser) { event.preventDefault(); navigate(pageButton.dataset.page); return; }
    const button = event.target.closest("[data-action]");
    if (!button) return;
    const action = button.dataset.action;
    try {
      if (action === "open-login" || action === "open-register") {
        $("#auth-layer").hidden = false; setAuthMode(action === "open-register" ? "register" : "login");
        refreshIcons();
      } else if (action === "close-auth") $("#auth-layer").hidden = true;
      else if (action === "show-login" || action === "show-register") setAuthMode(action.slice(5));
      else if (action === "logout") await signOut();
      else if (action === "start-exam") await startExam(button.dataset.id);
      else if (action === "resume-exam") await openAttempt(button.dataset.attempt);
      else if (action === "previous-question") { currentQuestion--; renderAttempt(); }
      else if (action === "next-question") { currentQuestion++; renderAttempt(); }
      else if (action === "jump-question") { currentQuestion = Number(button.dataset.index); renderAttempt(); }
      else if (action === "toggle-review") {
        const q = activeQuestions[currentQuestion];
        const state = answers.get(q.id) || { selectedOption: null, markedForReview: false };
        state.markedForReview = !state.markedForReview;
        answers.set(q.id, state);
        await queueAnswer(q.id);
        renderAttempt();
      } else if (action === "confirm-submit") {
        const answered = [...answers.values()].filter(a => a.selectedOption).length;
        const review = [...answers.values()].filter(a => a.markedForReview).length;
        const unanswered = activeQuestions.length - answered;
        if (window.confirm(`Submit this examination?\n\nAnswered: ${answered}\nUnanswered: ${unanswered}\nMarked for review: ${review}\n\nYou cannot change answers after submission.`)) await submitAttempt();
      } else if (action === "download-results") downloadCsv();
      else if (action === "manage-questions") await showQuestionManager(Number(button.dataset.id));
      else if (action === "edit-exam") {
        const exam = teacherExamCache.find(item => item.id === Number(button.dataset.id));
        if (!exam) throw new Error("Examination not found.");
        const form = $("#exam-create-form");
        form.dataset.examId = exam.id;
        $("#exam-form-title").textContent = "Edit examination";
        form.elements.title.value = exam.title;
        form.elements.subjectId.value = String(teacherSubjectsCache.find(s => s.code === exam.subjectCode)?.id || "");
        form.elements.startTime.value = dateTimeLocal(exam.startTime);
        form.elements.endTime.value = dateTimeLocal(exam.endTime);
        form.elements.durationMinutes.value = exam.durationMinutes;
        form.elements.totalMarks.value = exam.totalMarks;
        form.elements.passingMarks.value = exam.passingMarks;
        $("button[type=submit]", form).textContent = "Save examination changes";
        form.scrollIntoView({ behavior: "smooth", block: "start" });
      } else if (action === "delete-exam") {
        if (window.confirm("Delete this examination and its questions? Exams with student attempts cannot be deleted.")) {
          await api(`/api/teacher/exams/${button.dataset.id}`, { method: "DELETE" });
          toast("Examination deleted.");
          await renderTeacherExams();
        }
      } else if (action === "toggle-additional-attempt") {
        const enabled = button.dataset.enabled === "true";
        const actionText = enabled ? "Allow one additional attempt for this exam?" : "Disable the additional attempt for this exam?";
        if (window.confirm(actionText)) {
          await api(`/api/admin/exams/${button.dataset.id}/multiple-attempts`, { method: "PUT", body: { enabled } });
          toast(enabled ? "One additional attempt is now enabled." : "Additional attempts are disabled.");
          await renderAdminExams();
        }
      } else if (action === "edit-subject") {
        const subject = adminSubjectCache.find(item => item.id === Number(button.dataset.id));
        if (!subject) throw new Error("Subject not found.");
        const form = $("#subject-form");
        form.dataset.subjectId = subject.id;
        $("#subject-form-title").textContent = "Edit subject";
        form.elements.code.value = subject.code;
        form.elements.name.value = subject.name;
        form.elements.branchId.value = String((await api("/api/admin/branches")).find(b => b.code === subject.branchCode)?.id || "");
        form.elements.semester.value = subject.semester;
        form.elements.description.value = subject.description || "";
        form.elements.teacherId.value = subject.teacherId ? String(subject.teacherId) : "";
        form.elements.totalMarks.value = subject.totalMarks;
        form.elements.passingMarks.value = subject.passingMarks;
        form.elements.durationMinutes.value = subject.durationMinutes;
        form.elements.available.checked = subject.available;
        $("button[type=submit]", form).textContent = "Save subject changes";
        $('[data-action="cancel-subject-edit"]', form).hidden = false;
        form.scrollIntoView({ behavior: "smooth", block: "start" });
      } else if (action === "cancel-subject-edit") {
        const form = $("#subject-form");
        delete form.dataset.subjectId;
        form.reset();
        $("#subject-form-title").textContent = "Add a subject";
        $("button[type=submit]", form).textContent = "Save subject";
        button.hidden = true;
      } else if (action === "delete-subject") {
        if (window.confirm("Delete this subject? Subjects with examinations cannot be deleted; mark them unavailable instead.")) {
          await api(`/api/admin/subjects/${button.dataset.id}`, { method: "DELETE" });
          toast("Subject deleted.");
          await renderAdminSubjects();
          await loadPublicData();
        }
      }
      else if (action === "edit-question") {
        const q = teacherQuestionCache.find(item => item.id === Number(button.dataset.id));
        if (!q) throw new Error("Question not found.");
        const form = $("#question-form");
        form.dataset.questionId = q.id;
        $("#question-form-title").textContent = "Edit question";
        Object.entries(q).forEach(([key, value]) => { if (form.elements[key]) form.elements[key].value = value ?? ""; });
        $('[data-action="cancel-question-edit"]', form).hidden = false;
        form.scrollIntoView({ behavior: "smooth", block: "start" });
      } else if (action === "cancel-question-edit") {
        const form = $("#question-form");
        delete form.dataset.questionId;
        form.reset();
        $("#question-form-title").textContent = "Add a question";
        button.hidden = true;
      } else if (action === "delete-question") {
        if (window.confirm("Delete this question? This cannot be undone.")) {
          await api(`/api/teacher/exams/${button.dataset.exam}/questions/${button.dataset.id}`, { method: "DELETE" });
          toast("Question deleted.");
          await showQuestionManager(Number(button.dataset.exam));
        }
      } else if (action === "open-teacher-form") $("#teacher-create-panel").hidden = false;
      else if (action === "close-teacher-form") $("#teacher-create-panel").hidden = true;
    } catch (error) { showError(error); }
  });

  document.addEventListener("change", async event => {
    if (event.target.matches("[data-semester-branch]")) {
      try { await renderBranches(Number(event.target.value)); } catch (error) { showError(error); }
      return;
    }
    if (event.target.matches('input[name="exam-answer"]') && activeAttempt) {
      const q = activeQuestions[currentQuestion];
      const state = answers.get(q.id) || { markedForReview: false };
      state.selectedOption = event.target.value;
      answers.set(q.id, state);
      renderAttempt();
      try { await queueAnswer(q.id); } catch (error) { showError(error); }
    }
  });

  document.addEventListener("input", event => {
    const input = event.target.closest("[data-filter]");
    if (!input) return;
    const table = document.getElementById(input.dataset.filter);
    if (!table) return;
    const term = input.value.toLocaleLowerCase();
    $$("tbody tr", table).forEach(row => { row.hidden = !row.textContent.toLocaleLowerCase().includes(term); });
  });

  $("#login-form").addEventListener("submit", onAuthSubmit);
  $("#register-form").addEventListener("submit", onAuthSubmit);
  workspace.addEventListener("submit", onWorkspaceSubmit);
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && !$("#auth-layer").hidden) $("#auth-layer").hidden = true;
  });

  (async () => {
    await loadPublicData();
    refreshIcons();
    try {
      const user = await api("/api/auth/me");
      showUser(user);
    } catch { /* Anonymous visitors stay on the public landing page. */ }
  })();
})();
