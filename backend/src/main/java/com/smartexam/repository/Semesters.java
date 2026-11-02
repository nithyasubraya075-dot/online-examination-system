package com.smartexam.repository;

import com.smartexam.entity.Domain.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface Semesters extends JpaRepository<Semester, Long> {
    Optional<Semester> findByBranchIdAndNumber(Long branchId, int number);
    List<Semester> findByBranchIdOrderByNumber(Long branchId);
}
