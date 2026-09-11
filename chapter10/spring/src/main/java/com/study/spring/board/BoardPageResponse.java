package com.study.spring.board;

import java.util.List;

import org.springframework.data.domain.Page;

public record BoardPageResponse(
        List<BoardApiResponse> boards,
        int currentPage,
        int totalPages,
        long totalElements,
        int size,
        boolean first,
        boolean last) {
    public static BoardPageResponse from(Page<Board> page) {
        return new BoardPageResponse(
                page.getContent().stream()
                        .map(BoardApiResponse::from)
                        .toList(),
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getSize(),
                page.isFirst(),
                page.isLast());
    }
}
