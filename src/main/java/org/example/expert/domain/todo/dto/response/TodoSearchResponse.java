package org.example.expert.domain.todo.dto.response;

public record TodoSearchResponse(
        Long id,
        String title,
        Long managerCount,
        Long commentCount
        ) {
}
