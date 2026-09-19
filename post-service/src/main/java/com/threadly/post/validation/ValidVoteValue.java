package com.threadly.post.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidVoteValueValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidVoteValue {
    String message() default "Vote value must be 1 (upvote) or -1 (downvote)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
