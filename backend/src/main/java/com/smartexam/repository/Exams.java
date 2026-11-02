package com.smartexam.repository;

import com.smartexam.entity.Domain.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface Exams extends JpaRepository<Exam, Long> {
    List<Exam> findBySubjectBranchIdAndSubjectSemesterNumber(Long branchId, int number);
    List<Exam> findBySubjectTeacherUserId(Long userId);
    long countBySubjectId(Long subjectId);
}
