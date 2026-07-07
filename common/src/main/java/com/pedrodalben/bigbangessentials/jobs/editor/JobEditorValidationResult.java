package com.pedrodalben.bigbangessentials.jobs.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record JobEditorValidationResult(
    boolean valid,
    String jobId,
    List<ValidationError> errors,
    List<ValidationWarning> warnings,
    List<String> infoMessages
) {
    public static JobEditorValidationResult valid(String jobId) {
        return new JobEditorValidationResult(true, jobId,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public static JobEditorValidationResult invalid(String jobId, List<ValidationError> errors) {
        return new JobEditorValidationResult(false, jobId,
            errors, Collections.emptyList(), Collections.emptyList());
    }

    public JobEditorValidationResult withWarnings(List<ValidationWarning> warnings) {
        return new JobEditorValidationResult(valid, jobId, errors, warnings, infoMessages);
    }

    public JobEditorValidationResult withInfo(List<String> info) {
        return new JobEditorValidationResult(valid, jobId, errors, warnings, info);
    }

    public record ValidationError(String field, String value, String cause, String suggestion) {
        @Override
        public String toString() {
            return String.format("[ERRO] Campo '%s' com valor '%s': %s. Sugestão: %s",
                field, value, cause, suggestion);
        }
    }

    public record ValidationWarning(String field, String value, String message) {
        @Override
        public String toString() {
            return String.format("[AVISO] Campo '%s' com valor '%s': %s",
                field, value, message);
        }
    }

    public static class Builder {
        private final String jobId;
        private boolean valid = true;
        private final List<ValidationError> errors = new ArrayList<>();
        private final List<ValidationWarning> warnings = new ArrayList<>();
        private final List<String> infoMessages = new ArrayList<>();

        public Builder(String jobId) { this.jobId = jobId; }

        public Builder addError(String field, String value, String cause, String suggestion) {
            valid = false;
            errors.add(new ValidationError(field, value, cause, suggestion));
            return this;
        }

        public Builder addWarning(String field, String value, String message) {
            warnings.add(new ValidationWarning(field, value, message));
            return this;
        }

        public Builder addInfo(String message) {
            infoMessages.add(message);
            return this;
        }

        public Builder merge(JobEditorValidationResult other) {
            errors.addAll(other.errors);
            warnings.addAll(other.warnings);
            infoMessages.addAll(other.infoMessages);
            if (!other.valid) valid = false;
            return this;
        }

        public JobEditorValidationResult build() {
            return new JobEditorValidationResult(valid, jobId,
                Collections.unmodifiableList(errors),
                Collections.unmodifiableList(warnings),
                Collections.unmodifiableList(infoMessages));
        }
    }
}
