package com.tikkle.investment.dto.response;

import java.util.List;

public record AiCandidateResponse(
        List<AiRecommendationDto> candidates
) {}