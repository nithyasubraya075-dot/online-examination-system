package com.smartexam.repository;

import com.smartexam.entity.Domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface Subjects extends JpaRepository<Subject, Long> {
    List<Subject> findByBranchIdAndSemesterNumberAndAvailableTrue(Long branchId, int number);
    List<Subject> findByBranchIdAndSemesterNumber(Long branchId, int number);
    List<Subject> findByTeacherUserId(Long userId);
    boolean existsByCodeIgnoreCase(String code);
}
