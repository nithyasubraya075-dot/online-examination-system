package com.smartexam.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttemptExpiryScheduler {
    private final ExamService exams;
    public AttemptExpiryScheduler(ExamService exams) { this.exams = exams; }

    @Scheduled(fixedDelayString = "${smartexam.expiry-scan-ms:30000}")
    public void submitExpiredAttempts() { exams.submitExpiredAttempts(); }
}
