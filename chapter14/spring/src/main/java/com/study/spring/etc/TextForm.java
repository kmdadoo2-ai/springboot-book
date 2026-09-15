package com.study.spring.etc;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TextForm {

    @Size(max = 1000, message = "1000자 이내로 입력하세요.")
    private String text = "";
}
