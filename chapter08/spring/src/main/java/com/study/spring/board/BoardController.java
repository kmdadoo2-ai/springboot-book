package com.study.spring.board;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/jpa/boards")
    public String list(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @PageableDefault(size = 5, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        Page<Board> boardPage;

        if (keyword.isBlank()) {
            boardPage = boardService.findAll(pageable);
        } else {
            boardPage = boardService.search(keyword, pageable);
        }

        int startPage = Math.max(1, boardPage.getNumber() - 2);
        int endPage = Math.min(boardPage.getTotalPages(), boardPage.getNumber() + 4);

        model.addAttribute("boardPage", boardPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);

        return "jpa/list";
    }

    @GetMapping("/jpa/boards/new")
    public String createForm(Model model) {
        model.addAttribute("boardForm", new BoardForm());
        return "jpa/form";
    }

    @PostMapping("/jpa/boards")
    public String save(@Valid BoardForm boardForm, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "jpa/form";
        }

        boardService.save(boardForm);
        return "redirect:/jpa/boards";
    }

    @GetMapping("/jpa/boards/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("board", boardService.findById(id));
        return "jpa/detail";
    }

    @GetMapping("/jpa/boards/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        Board board = boardService.findById(id);

        BoardForm boardForm = new BoardForm();
        boardForm.setTitle(board.getTitle());
        boardForm.setContent(board.getContent());
        boardForm.setWriter(board.getWriter());

        model.addAttribute("boardId", id);
        model.addAttribute("boardForm", boardForm);

        return "jpa/form";
    }

    @PostMapping("/jpa/boards/{id}/edit")
    public String update(
            @PathVariable("id") Long id,
            @Valid BoardForm boardForm,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "jpa/form";
        }

        boardService.update(id, boardForm);
        return "redirect:/jpa/boards/" + id;
    }

    @PostMapping("/jpa/boards/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        boardService.delete(id);
        return "redirect:/jpa/boards";
    }
}
