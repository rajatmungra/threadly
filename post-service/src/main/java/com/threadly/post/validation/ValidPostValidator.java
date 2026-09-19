package com.threadly.post.validation;

import com.threadly.post.dto.request.CreatePostRequest;
import com.threadly.post.entity.PostType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;

public class ValidPostValidator implements ConstraintValidator<ValidPost, CreatePostRequest> {

    @Override
    public boolean isValid(CreatePostRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean valid = true;

        if (request.getTitle() != null && request.getTitle().trim().length() < 3) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Title must be between 3 and 300 characters")
                .addPropertyNode("title")
                .addConstraintViolation();
            valid = false;
        }

        if (request.getType() == null) {
            return valid;
        }

        if (request.getType() == PostType.TEXT) {
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Content is required for text posts")
                    .addPropertyNode("content")
                    .addConstraintViolation();
                valid = false;
            }
            if (request.getUrl() != null && !request.getUrl().trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("URL must not be provided for text posts")
                    .addPropertyNode("url")
                    .addConstraintViolation();
                valid = false;
            }
        } else if (request.getType() == PostType.LINK) {
            if (request.getUrl() == null || request.getUrl().trim().isEmpty()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("URL is required for link posts")
                    .addPropertyNode("url")
                    .addConstraintViolation();
                valid = false;
            } else if (!isValidUrl(request.getUrl())) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("URL must be a valid HTTP or HTTPS URL")
                    .addPropertyNode("url")
                    .addConstraintViolation();
                valid = false;
            }
        }

        return valid;
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return false;
            }
            return uri.getHost() != null && !uri.getHost().trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
