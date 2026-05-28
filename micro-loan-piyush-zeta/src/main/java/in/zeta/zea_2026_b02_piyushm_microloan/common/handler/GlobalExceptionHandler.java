package in.zeta.zea_2026_b02_piyushm_microloan.common.handler;

import in.zeta.springframework.boot.commons.authentication.models.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.BusinessException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.DuplicateResourceException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.InvalidStateException;
import in.zeta.zea_2026_b02_piyushm_microloan.common.exception.ResourceNotFoundException;
import in.zeta.zea_2026_b02_piyushm_microloan.dto.common.ErrorResponse;

import java.util.Objects;

import java.time.Instant;
import java.util.List;

import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final SpectraLogger log = OlympusSpectra.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> ErrorResponse.FieldError.builder()
						.field(fe.getField())
						.message(fe.getDefaultMessage())
						.build())
				.toList();

		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.BAD_REQUEST.value())
				.error(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.code("VALIDATION_ERROR")
				.message("Validation failed")
				.path(request.getRequestURI())
				.fieldErrors(fieldErrors)
				.build();

		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex,
			HttpServletRequest request) {
		HttpStatus status = ex.getErrorCode().getHttpStatus();
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(status.value())
				.error(status.getReasonPhrase())
				.code(ex.getErrorCode().name())
				.message(ex.getMessage())
				.path(request.getRequestURI())
				.build();

		return ResponseEntity.status(status).body(body);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
			HttpServletRequest request) {
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.NOT_FOUND.value())
				.error(HttpStatus.NOT_FOUND.getReasonPhrase())
				.code(ex.getErrorCode().name())
				.message(ex.getMessage())
				.path(request.getRequestURI())
				.build();

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(InvalidStateException.class)
	public ResponseEntity<ErrorResponse> handleInvalidState(InvalidStateException ex,
			HttpServletRequest request) {
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.CONFLICT.value())
				.error(HttpStatus.CONFLICT.getReasonPhrase())
				.code(ex.getErrorCode().name())
				.message(ex.getMessage())
				.path(request.getRequestURI())
				.build();

		return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex,
			HttpServletRequest request) {
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.CONFLICT.value())
				.error(HttpStatus.CONFLICT.getReasonPhrase())
				.code(ex.getErrorCode().name())
				.message(ex.getMessage())
				.path(request.getRequestURI())
				.build();

		return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.CONFLICT.value())
				.error(HttpStatus.CONFLICT.getReasonPhrase())
				.code("DATA_INTEGRITY_ERROR")
				.message("A duplicate or constraint violation occurred")
				.path(request.getRequestURI())
				.build();

		return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException ex,
			HttpServletRequest request) {
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.NOT_FOUND.value())
				.error(HttpStatus.NOT_FOUND.getReasonPhrase())
				.code("ENDPOINT_NOT_FOUND")
				.message("No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL())
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		String message = "Malformed request body";
		Throwable cause = ex.getCause();
		if (cause != null && cause.getMessage() != null) {
			message = "Invalid request body: " + cause.getMessage().split("\n")[0];
		}

		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.BAD_REQUEST.value())
				.error(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.code("INVALID_REQUEST_BODY")
				.message(message)
				.path(request.getRequestURI())
				.build();

		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request) {
		String supported = ex.getSupportedHttpMethods() != null
				? ex.getSupportedHttpMethods().toString()
				: "unknown";
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.METHOD_NOT_ALLOWED.value())
				.error(HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase())
				.code("METHOD_NOT_ALLOWED")
				.message("HTTP method '" + ex.getMethod() + "' is not supported for this endpoint. Supported: "
						+ supported)
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
	}

	/**
	 * Handles authentication failures only (missing/invalid/expired token, or
	 * authentication service timeout).
	 * Returns HTTP 401 (Unauthorized) for authentication failures. Not for
	 * authorization (permission/forbidden) errors—those are handled separately with
	 * 403.
	 */
	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex,
			HttpServletRequest request) {
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.UNAUTHORIZED.value())
				.error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
				.code("UNAUTHORIZED")
				.message("Authentication failed: " + ex.getMessage())
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
	}

	@ExceptionHandler(in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.UnauthorizedException.class)
	public ResponseEntity<ErrorResponse> handleAuthorizationDenied(
			in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.UnauthorizedException ex,
			HttpServletRequest request) {
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.FORBIDDEN.value())
				.error(HttpStatus.FORBIDDEN.getReasonPhrase())
				.code("FORBIDDEN")
				.message("Access denied: " + ex.getMessage())
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneric(Exception ex,
			HttpServletRequest request) {
		log.error("Unhandled exception", ex).attr("path", request.getRequestURI())
				.attr("error", ex.getMessage()).log();

		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
				.error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
				.code("INTERNAL_ERROR")
				.message("An unexpected error occurred")
				.path(request.getRequestURI())
				.build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

	/**
	 * Handles invalid path/query parameter types (e.g., malformed UUIDs).
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		String param = ex.getName();
		String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
		String value = Objects.toString(ex.getValue(), "null");
		String message = "Invalid value '" + value + "' for parameter '" + param + "'. Expected type: "
				+ requiredType + ".";

		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.BAD_REQUEST.value())
				.error(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.code("INVALID_PARAMETER")
				.message(message)
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.badRequest().body(body);
	}

	/**
	 * Handles IllegalArgumentException globally (e.g., for uncaught argument
	 * errors).
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
			HttpServletRequest request) {
		ErrorResponse body = ErrorResponse.builder()
				.timestamp(Instant.now())
				.status(HttpStatus.BAD_REQUEST.value())
				.error(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.code("ILLEGAL_ARGUMENT")
				.message(ex.getMessage() != null ? ex.getMessage() : "Invalid argument")
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.badRequest().body(body);
	}
}
