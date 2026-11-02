package com.smartexam.controller;

import com.smartexam.dto.Api.BranchView;
import com.smartexam.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final AcademicService academic; private final DashboardService dashboard;
    public PublicController(AcademicService academic, DashboardService dashboard) { this.academic = academic; this.dashboard = dashboard; }
    @GetMapping("/branches") public List<BranchView> branches() { return academic.branches(); }
    @GetMapping("/stats") public Map<String, Long> stats() { return dashboard.publicStats(); }
}
