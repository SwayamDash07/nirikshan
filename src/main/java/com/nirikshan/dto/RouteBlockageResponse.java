package com.nirikshan.dto;

import java.util.List;

public record RouteBlockageResponse(String status, String reason, List<String> evidence, String source) { }
