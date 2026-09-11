package com.study.spring.board;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/boards")
public class BoardController {

    private final BoardDao boardDao;

    public BoardController(BoardDao boardDao) {
        this.boardDao = boardDao;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("boards", boardDao.findAll());
        return "board/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("board", boardDao.findById(id));
        return "board/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("board", new Board());
        return "board/form";
    }

    @PostMapping
    public String create(Board board) {
        boardDao.save(board);
        return "redirect:/boards";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("board", boardDao.findById(id));
        return "board/form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Board board) {
        boardDao.update(id, board);
        return "redirect:/boards/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        boardDao.delete(id);
        return "redirect:/boards";
    }
}
