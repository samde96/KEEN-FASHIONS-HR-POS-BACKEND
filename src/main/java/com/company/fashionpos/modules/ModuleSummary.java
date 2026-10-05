package com.company.fashionpos.modules;

import java.util.List;

public record ModuleSummary(
    String moduleKey,
    String title,
    String description,
    List<ModuleMetric> metrics,
    List<ModuleWorkItem> workItems) {}
