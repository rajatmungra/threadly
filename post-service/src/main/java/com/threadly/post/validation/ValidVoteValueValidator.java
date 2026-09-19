package com.threadly.post.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidVoteValueValidator implements ConstraintValidator<ValidVoteValue, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value == 1 || value == -1;
    }
}
