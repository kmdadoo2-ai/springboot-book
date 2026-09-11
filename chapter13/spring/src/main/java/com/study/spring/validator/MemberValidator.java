package com.study.spring.validator;

import com.study.spring.form.MemberForm;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

@Component
public class MemberValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return MemberForm.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ValidationUtils.rejectIfEmptyOrWhitespace(
                errors,
                "name",
                "required",
                "이름을 입력하세요.");

        MemberForm form = (MemberForm) target;

        if (form.getPassword() == null || form.getPassword().length() < 4) {
            errors.rejectValue("password", "short", "비밀번호는 4자 이상 입력하세요.");
        }
    }
}