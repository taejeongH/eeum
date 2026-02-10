export const Logger = {
  info: (...args) => {
    if (import.meta.env.DEV) {
      console.info('ℹ️ [INFO]', ...args);
    }
  },

  debug: (...args) => {
    if (import.meta.env.DEV) {
      console.debug('🐛 [DEBUG]', ...args);
    }
  },

  warn: (...args) => {
    console.warn('⚠️ [WARN]', ...args);
  },

  error: (message, error = null) => {
    const errorDetails = error ? error : '';
    console.error('🚨 [ERROR]', message, errorDetails);
  },
};

export default Logger;
