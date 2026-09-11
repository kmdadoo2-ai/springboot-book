package com.study.spring.board;

import jakarta.validation.constraints.NotBlank;

public record BoardApiRequest(
        @NotBlank(message = "제목을 입력하세요.") String title,

        @NotBlank(message = "내용을 입력하세요.") String content,

        @NotBlank(message = "작성자를 입력하세요.") String writer) {
    public BoardForm toForm() {
        BoardForm form = new BoardForm();
        form.setTitle(title);
        form.setContent(content);
        form.setWriter(writer);
        return form;
    }
}
