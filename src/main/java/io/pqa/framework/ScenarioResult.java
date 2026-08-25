package io.pqa.framework;

public record ScenarioResult(String id, String name, boolean passed, String detail, long durationMs) {}
