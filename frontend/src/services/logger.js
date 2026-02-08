/**
 * Centralized Logger Service for Production
 * - Controls console output based on environment
 * - Wraps errors for future collection (e.g., Sentry)
 */

export const Logger = {
    /**
     * Log informative messages (Hidden in Production)
     */
    info: (...args) => {
        if (import.meta.env.DEV) {
            console.info("ℹ️ [INFO]", ...args);
        }
    },

    /**
     * Log debug messages (Hidden in Production)
     */
    debug: (...args) => {
        if (import.meta.env.DEV) {
            console.debug("🐛 [DEBUG]", ...args);
        }
    },

    /**
     * Log warnings (Controlled in Production)
     * - Use for non-critical issues or deprecations
     */
    warn: (...args) => {
        // Always show warnings, but format them potentially
        console.warn("⚠️ [WARN]", ...args);
    },

    /**
     * Log errors and collect them (Always visible)
     * - Use for exceptions, API failures, etc.
     */
    error: (message, error = null) => {
        const errorDetails = error ? error : '';
        console.error("🚨 [ERROR]", message, errorDetails);

        // TODO: Integrate Sentry or other error reporting service here
        // if (import.meta.env.PROD && window.Sentry) {
        //     window.Sentry.captureException(error || new Error(message));
        // }
    }
};

export default Logger;
