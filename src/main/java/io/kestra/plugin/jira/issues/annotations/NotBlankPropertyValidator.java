package io.kestra.plugin.jira.issues.annotations;

import io.kestra.core.models.property.Property;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

public class NotBlankPropertyValidator
    implements ConstraintValidator<NotBlankProperty, Property<?>> {

    @Override
    public boolean isValid(Property<?> property, ConstraintValidatorContext context) {

        if (property == null) {
            return false;
        }

        return !StringUtils.isBlank(property.toString());
    }
}