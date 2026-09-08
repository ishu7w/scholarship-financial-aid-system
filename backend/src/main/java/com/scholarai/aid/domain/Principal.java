package com.scholarai.aid.domain;

public record Principal(String id, String name, String role, boolean demo) {
    public Principal {
        if (id == null || id.isBlank() || id.length() > 200 || name == null || name.isBlank() || name.length() > 200 || !java.util.Set.of("student", "institution", "admin").contains(role == null ? "" : role))
            throw new AidException(401, "Invalid signed identity.");
    }
    public boolean canReview() { return role.equals("admin") || demo; }
}
