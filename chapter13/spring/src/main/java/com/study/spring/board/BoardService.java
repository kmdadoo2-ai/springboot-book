package com.study.spring.board;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;

    public Page<Board> findAll(Pageable pageable) {
        return boardRepository.findAll(pageable);
    }

    public Page<Board> search(String keyword, Pageable pageable) {
        return boardRepository.findByTitleContaining(keyword, pageable);
    }

    public Board findById(Long id) {
        return boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
    }

    @Transactional
    public Board save(BoardForm form) {
        Board board = Board.builder()
                .title(form.getTitle())
                .content(form.getContent())
                .writer(form.getWriter())
                .build();

        return boardRepository.save(board);
    }

    @Transactional
    public void update(Long id, BoardForm form) {
        Board board = findById(id);
        board.update(form.getTitle(), form.getContent(), form.getWriter());
    }

    @Transactional
    public void delete(Long id) {
        boardRepository.deleteById(id);
    }

    public boolean existsById(Long id) {
        return boardRepository.existsById(id);
    }
}