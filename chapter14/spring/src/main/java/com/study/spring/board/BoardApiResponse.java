package com.study.spring.board;

import java.time.LocalDateTime;

public record BoardApiResponse(
        Long id,
        String title,
        String content,
        String writer,
        LocalDateTime createdAt) {
    public static BoardApiResponse from(Board board) {
        return new BoardApiResponse(
                board.getId(),
                board.getTitle(),
                board.getContent(),
                board.getWriter(),
                board.getCreatedAt());
    }
}
