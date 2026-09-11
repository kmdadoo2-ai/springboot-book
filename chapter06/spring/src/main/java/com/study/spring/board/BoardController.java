package com.study.spring.board;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Controller
@RequestMapping("/jpa/boards")
public class BoardController {

    // private final BoardDao boardDao;
    // private final BoardMapper boardMapper;
    private final BoardRepository boardRepository;

    public BoardController(BoardRepository boardRepository) {
        this.boardRepository = boardRepository;
    }

    @GetMapping
    public String list(Pageable pageable, Model model) {
        Page<Board> board = boardRepository.findAll(pageable);
        model.addAttribute("boards", board);
        return "jpa/list-page";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("boardForm", new BoardForm());
        return "jpa/form";
    }

    @PostMapping
    public String save(BoardForm form) {
        Board board = new Board(form.getTitle(), form.getContent(), form.getWriter());
        boardRepository.save(board);
        return "redirect:/jpa/boards";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        Board board = boardRepository.findById(id).orElseThrow();
        model.addAttribute("board", board);
        return "jpa/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        Board board = boardRepository.findById(id).orElseThrow();

        BoardForm form = new BoardForm();
        form.setTitle(board.getTitle());
        form.setContent(board.getContent());
        form.setWriter(board.getWriter());

        model.addAttribute("boardId", id);
        model.addAttribute("boardForm", form);
        return "jpa/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable("id") Long id, BoardForm form) {
        Board board = boardRepository.findById(id).orElseThrow();
        board.update(form.getTitle(), form.getContent(), form.getWriter());
        boardRepository.save(board);
        return "redirect:/jpa/boards/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        boardRepository.deleteById(id);
        return "redirect:/jpa/boards";
    }

    @GetMapping("/search")
    public String search(@RequestParam String keyword, Pageable pageable, Model model) {
        model.addAttribute("boards", boardRepository.findByTitleContaining(keyword, pageable));
        model.addAttribute("keyword", keyword);
        return "jpa/search";
    }
}
