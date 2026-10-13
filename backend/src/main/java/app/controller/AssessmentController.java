
package app.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.dto.SubmitAnswerRequest;
import app.entity.Assessment;
import app.entity.Question;
import app.entity.QuizAttempt;
import app.entity.User;
import app.repository.AssessmentRepository;
import app.repository.QuestionRepository;
import app.repository.QuizAttemptRepository;
import app.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/assessments")
@CrossOrigin(origins = "*")
public class AssessmentController {

    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final UserRepository userRepository;

    public AssessmentController(
            AssessmentRepository assessmentRepository,
            QuestionRepository questionRepository,
            QuizAttemptRepository quizAttemptRepository,
            UserRepository userRepository) {

        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.userRepository = userRepository;
    }

    private ResponseEntity<Map<String, String>> checkTeacher(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null
                || session.getAttribute("userId") == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Please log in first"));
        }

        Object value = session.getAttribute("userId");

        if (!(value instanceof Long)) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Invalid session. Please log in again"));
        }

        Long userId = (Long) value;
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            session.invalidate();
            return ResponseEntity.status(401)
                    .body(Map.of("message", "User not found. Please log in again"));
        }

        // Always use the current role from the database.
        session.setAttribute("username", user.getUsername());
        session.setAttribute("role", user.getRole());

        if (!"TEACHER".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(403)
                    .body(Map.of("message", "Teacher access required"));
        }

        return null;
    }

    @PostMapping
    public ResponseEntity<?> createAssessment(
            @RequestBody Assessment assessment,
            HttpServletRequest request) {

        ResponseEntity<Map<String, String>> accessError =
                checkTeacher(request);

        if (accessError != null) {
            return accessError;
        }

        HttpSession session = request.getSession(false);
        Long teacherId = (Long) session.getAttribute("userId");

        if (assessment.getTitle() == null
                || assessment.getTitle().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Assessment title is required"));
        }

        // Set the creator from the authenticated session.
        assessment.setId(null);
        assessment.setCreatedBy(teacherId);

        if (assessment.getQuestions() == null) {
            assessment.setQuestions(new ArrayList<>());
        } else {
            for (Question question : assessment.getQuestions()) {
                question.setId(null);
                question.setAssessment(assessment);
            }
        }

        Assessment saved = assessmentRepository.save(assessment);

        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "title", saved.getTitle(),
                "description", saved.getDescription() == null
                        ? "" : saved.getDescription(),
                "message", "Assessment created successfully"
        ));
    }

    @GetMapping
    public ResponseEntity<?> getAllAssessments() {

        List<Map<String, Object>> response =
                assessmentRepository.findAll()
                        .stream()
                        .map(assessment -> {
                            Map<String, Object> item = new HashMap<>();
                            item.put("id", assessment.getId());
                            item.put("title", assessment.getTitle());
                            item.put("description", assessment.getDescription());
                            item.put("createdBy", assessment.getCreatedBy());
                            return item;
                        })
                        .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAssessmentById(
            @PathVariable Long id) {

        Assessment assessment =
                assessmentRepository.findById(id).orElse(null);

        if (assessment == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", assessment.getId());
        response.put("title", assessment.getTitle());
        response.put("description", assessment.getDescription());
        response.put("createdBy", assessment.getCreatedBy());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/questions")
    public ResponseEntity<?> addQuestion(
            @PathVariable Long id,
            @RequestBody Question question,
            HttpServletRequest request) {

        ResponseEntity<Map<String, String>> accessError =
                checkTeacher(request);

        if (accessError != null) {
            return accessError;
        }

        Assessment assessment =
                assessmentRepository.findById(id).orElse(null);

        if (assessment == null) {
            return ResponseEntity.notFound().build();
        }

        if (question.getText() == null
                || question.getText().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Question text is required"));
        }

        question.setId(null);
        question.setAssessment(assessment);

        Question saved = questionRepository.save(question);

        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "text", saved.getText(),
                "optionA", saved.getOptionA() == null ? "" : saved.getOptionA(),
                "optionB", saved.getOptionB() == null ? "" : saved.getOptionB(),
                "optionC", saved.getOptionC() == null ? "" : saved.getOptionC(),
                "optionD", saved.getOptionD() == null ? "" : saved.getOptionD(),
                "message", "Question added successfully"
        ));
    }

    @GetMapping("/{id}/questions")
    public ResponseEntity<?> getQuestions(
            @PathVariable Long id) {

        if (!assessmentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> response =
                questionRepository.findByAssessmentId(id)
                        .stream()
                        .map(question -> {
                            Map<String, Object> item = new HashMap<>();
                            item.put("id", question.getId());
                            item.put("text", question.getText());
                            item.put("optionA", question.getOptionA());
                            item.put("optionB", question.getOptionB());
                            item.put("optionC", question.getOptionC());
                            item.put("optionD", question.getOptionD());
                            return item;
                        })
                        .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitAnswers(
            @PathVariable Long id,
            @RequestBody List<SubmitAnswerRequest> answers,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Please log in first"));
        }

        if (!assessmentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        if (answers == null || answers.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "At least one answer is required"));
        }

        List<Question> questions = questionRepository.findByAssessmentId(id);

        if (questions.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "This assessment has no questions"));
        }

        Map<Long, Question> questionMap = new HashMap<>();
        for (Question question : questions) {
            questionMap.put(question.getId(), question);
        }

        Set<Long> submittedQuestionIds = new HashSet<>();
        int score = 0;
        int answered = 0;

        for (SubmitAnswerRequest answer : answers) {

            if (answer == null || answer.getQuestionId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Question ID is required"));
            }

            Long questionId = answer.getQuestionId();

            if (!submittedQuestionIds.add(questionId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Duplicate question ID: " + questionId));
            }

            Question question = questionMap.get(questionId);

            if (question == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message",
                                "Question does not belong to this assessment"));
            }

            String selected = answer.getSelectedAnswer() == null
                    ? ""
                    : answer.getSelectedAnswer().trim().toUpperCase();

            if (!selected.isEmpty()
                    && !List.of("A", "B", "C", "D").contains(selected)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Answer must be A, B, C, or D"));
            }

            if (!selected.isEmpty()) {
                answered++;

                if (question.getCorrectAnswer() != null
                        && question.getCorrectAnswer().trim()
                                .equalsIgnoreCase(selected)) {
                    score++;
                }
            }
        }

        int totalQuestions = questions.size();
        int unanswered = totalQuestions - answered;
        double percentage = Math.round(
                (score * 10000.0) / totalQuestions) / 100.0;

        QuizAttempt attempt = new QuizAttempt();
        attempt.setAssessmentId(id);
        attempt.setStudentName(
                String.valueOf(session.getAttribute("username")));
        attempt.setScore(score);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setPercentage(percentage);
        attempt.setSubmittedAt(LocalDateTime.now());

        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);

        Map<String, Object> result = new HashMap<>();
        result.put("attemptId", savedAttempt.getId());
        result.put("assessmentId", id);
        result.put("totalQuestions", totalQuestions);
        result.put("answeredQuestions", answered);
        result.put("correctAnswers", score);
        result.put("incorrectAnswers", answered - score);
        result.put("unansweredQuestions", unanswered);
        result.put("score", score + "/" + totalQuestions);
        result.put("percentage", percentage);
        result.put("message", "Quiz submitted and result saved successfully");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/attempts")
    public ResponseEntity<?> getAssessmentAttempts(
            @PathVariable Long id,
            HttpServletRequest request) {

        ResponseEntity<Map<String, String>> accessError =
                checkTeacher(request);

        if (accessError != null) {
            return accessError;
        }

        if (!assessmentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> results =
                quizAttemptRepository.findByAssessmentIdOrderBySubmittedAtDesc(id)
                        .stream()
                        .map(attempt -> {
                            Map<String, Object> item = new HashMap<>();
                            item.put("attemptId", attempt.getId());
                            item.put("assessmentId", attempt.getAssessmentId());
                            item.put("studentName", attempt.getStudentName());
                            item.put("score", attempt.getScore());
                            item.put("totalQuestions", attempt.getTotalQuestions());
                            item.put("percentage", attempt.getPercentage());
                            item.put("submittedAt", attempt.getSubmittedAt());
                            return item;
                        })
                        .toList();

        return ResponseEntity.ok(results);
    }
}
