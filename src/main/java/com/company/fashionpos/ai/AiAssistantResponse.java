package com.company.fashionpos.ai;

import java.util.List;

public record AiAssistantResponse(
    String answer, List<String> citations, List<String> suggestions) {}
