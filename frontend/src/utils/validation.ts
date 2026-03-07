// Email validation
export const isValidEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
};

// Password validation
export interface PasswordValidation {
    isValid: boolean;
    hasMinLength: boolean;
    hasUppercase: boolean;
    hasLowercase: boolean;
    hasNumber: boolean;
    hasSpecialChar: boolean;
    strength: 'weak' | 'medium' | 'strong';
}

export const validatePassword = (password: string): PasswordValidation => {
    const hasMinLength = password.length >= 8;
    const hasUppercase = /[A-Z]/.test(password);
    const hasLowercase = /[a-z]/.test(password);
    const hasNumber = /[0-9]/.test(password);
    const hasSpecialChar = /[!@#$%^&*(),.?":{}|<>]/.test(password);

    const criteriaCount = [hasMinLength, hasUppercase, hasLowercase, hasNumber, hasSpecialChar].filter(Boolean).length;

    let strength: 'weak' | 'medium' | 'strong' = 'weak';
    if (criteriaCount >= 4) strength = 'strong';
    else if (criteriaCount >= 3) strength = 'medium';

    return {
        isValid: hasMinLength && hasUppercase && hasLowercase && hasNumber,
        hasMinLength,
        hasUppercase,
        hasLowercase,
        hasNumber,
        hasSpecialChar,
        strength,
    };
};

// Check if passwords match
export const doPasswordsMatch = (password: string, confirmPassword: string): boolean => {
    return password === confirmPassword && password.length > 0;
};

// Name validation
export const isValidName = (name: string): boolean => {
    return name.trim().length >= 2;
};

// Form field error messages
export const getEmailError = (email: string): string | null => {
    if (!email) return 'Email is required';
    if (!isValidEmail(email)) return 'Please enter a valid email address';
    return null;
};

export const getPasswordError = (password: string): string | null => {
    if (!password) return 'Password is required';
    const validation = validatePassword(password);
    if (!validation.hasMinLength) return 'Password must be at least 8 characters';
    if (!validation.hasUppercase) return 'Password must contain an uppercase letter';
    if (!validation.hasLowercase) return 'Password must contain a lowercase letter';
    if (!validation.hasNumber) return 'Password must contain a number';
    return null;
};

export const getConfirmPasswordError = (password: string, confirmPassword: string): string | null => {
    if (!confirmPassword) return 'Please confirm your password';
    if (!doPasswordsMatch(password, confirmPassword)) return 'Passwords do not match';
    return null;
};

export const getNameError = (name: string): string | null => {
    if (!name) return 'Name is required';
    if (!isValidName(name)) return 'Name must be at least 2 characters';
    return null;
};
