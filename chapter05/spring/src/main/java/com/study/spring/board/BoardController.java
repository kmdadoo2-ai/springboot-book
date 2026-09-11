package com.study.spring.board;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/mybatis/boards")
public class BoardController {

    // private final BoardDao boardDao;
    private final BoardMapper boardMapper;

    public BoardController(BoardMapper boardMapper) {
        this.boardMapper = boardMapper;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("boards", boardMapper.findAll());
        return "mybatis/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("board", boardMapper.findById(id));
        return "mybatis/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("board", new Board());
        return "mybatis/form";
    }

    @PostMapping
    public String create(Board board) {
        boardMapper.save(board);
        return "redirect:/mybatis/boards";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("board", boardMapper.findById(id));
        return "mybatis/form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable("id") Long id, Board board) {
        boardMapper.update(id, board);
        return "redirect:/mybatis/boards/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        boardMapper.delete(id);
        return "redirect:/mybatis/boards";
    }
}
