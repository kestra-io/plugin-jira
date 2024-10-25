package io.kestra.plugin.jira.issues.annotations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Constraint(validatedBy = NotBlankPropertyValidator.class)
@Target({FIELD})
@Retention(RUNTIME)
@Documented
public @interface NotBlankProperty {
    String message() default "Property cannot be blank";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
