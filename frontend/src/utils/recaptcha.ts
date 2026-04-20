// reCAPTCHA v3 Configuration
export const RECAPTCHA_SITE_KEY = '6Lfo6HssAAAAAMbOpUBT3f99kLbmB_xdKi6sUM-S';

/**
 * Set VITE_RECAPTCHA_ENABLED=false in .env to disable reCAPTCHA entirely.
 * Must be in sync with backend: app.recaptcha.enabled in application.properties.
 */
export const RECAPTCHA_ENABLED = import.meta.env.VITE_RECAPTCHA_ENABLED !== 'false';

// reCAPTCHA actions for different forms
export const RECAPTCHA_ACTIONS = {
    LOGIN: 'login',
    SIGNUP: 'signup',
    CONTACT: 'contact',
} as const;

/**
 * Executes reCAPTCHA v3 and returns the token to be sent to the backend.
 * The actual verification (score check) happens server-side.
 * Returns null if reCAPTCHA is unavailable (e.g. ad blocker) — callers must handle this.
 */
export const executeRecaptchaToken = async (
    executeRecaptcha: ((action: string) => Promise<string>) | undefined,
    action: string
): Promise<string | null> => {
    // When reCAPTCHA is disabled (offline/dev mode), return a bypass token.
    // Backend must also have app.recaptcha.enabled=false to accept it.
    if (!RECAPTCHA_ENABLED) {
        return 'recaptcha-disabled';
    }

    if (!executeRecaptcha) {
        console.warn('reCAPTCHA not available');
        return null;
    }
    try {
        return await executeRecaptcha(action);
    } catch (err) {
        console.error('reCAPTCHA execution failed:', err);
        return null;
    }
};
