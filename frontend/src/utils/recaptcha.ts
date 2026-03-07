// reCAPTCHA v3 Configuration
export const RECAPTCHA_SITE_KEY = '6Lfo6HssAAAAAMbOpUBT3f99kLbmB_xdKi6sUM-S';

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
