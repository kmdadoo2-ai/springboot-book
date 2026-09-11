package com.study.spring.board;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BoardApiController {

    private final BoardService boardService;

    @GetMapping("/api/boards")
    public BoardPageResponse list(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @PageableDefault(size = 5, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Board> boardPage;

        if (keyword.isBlank()) {
            boardPage = boardService.findAll(pageable);
        } else {
            boardPage = boardService.search(keyword, pageable);
        }

        return BoardPageResponse.from(boardPage);
    }

    @GetMapping("/api/boards/{id}")
    public ResponseEntity<BoardApiResponse> detail(@PathVariable("id") Long id) {
        try {
            Board board = boardService.findById(id);
            return ResponseEntity.ok(BoardApiResponse.from(board));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/api/boards")
    public ResponseEntity<BoardApiResponse> save(
            @Valid @RequestBody BoardApiRequest request) {
        Board savedBoard = boardService.save(request.toForm());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BoardApiResponse.from(savedBoard));
    }

    @PutMapping("/api/boards/{id}")
    public ResponseEntity<BoardApiResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody BoardApiRequest request) {
        if (!boardService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        boardService.update(id, request.toForm());
        Board updatedBoard = boardService.findById(id);

        return ResponseEntity.ok(BoardApiResponse.from(updatedBoard));
    }

    @DeleteMapping("/api/boards/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        if (!boardService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        boardService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
