package com.study.spring.etc;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class TextService {

    public TextResult inspect(String text) {
        return new TextResult(
                StringUtils.isNotBlank(text),
                StringUtils.length(text),
                StringUtils.stripToEmpty(text));
    }

    public record TextResult(boolean hasText, int length, String stripped) {
    }
}
