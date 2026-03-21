import axios, { AxiosError } from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

// ─── Token Storage (with JWT format validation) ───────────────────────────────

const TOKEN_KEY = 'roadrunner_token';

/** Validates that a string looks like a JWT (3 base64url parts separated by dots). */
const isValidJwtFormat = (token: string): boolean => {
    const parts = token.split('.');
    if (parts.length !== 3) return false;
    const base64urlRegex = /^[A-Za-z0-9_-]+$/;
    return parts.every(part => base64urlRegex.test(part) && part.length > 0);
};

export const storeToken = (token: string): void => {
    if (!isValidJwtFormat(token)) {
        console.error('Attempted to store an invalid JWT token. Rejected.');
        return;
    }
    localStorage.setItem(TOKEN_KEY, token);
};

export const getStoredToken = (): string | null => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) return null;
    if (!isValidJwtFormat(token)) {
        localStorage.removeItem(TOKEN_KEY); // Remove corrupted token
        return null;
    }
    return token;
};

export const removeToken = (): void => {
    localStorage.removeItem(TOKEN_KEY);
};

// ─── Axios Instance ───────────────────────────────────────────────────────────

const api = axios.create({
    baseURL: BASE_URL,
    headers: { 'Content-Type': 'application/json' },
});

// Attach token to every request if available
api.interceptors.request.use((config) => {
    const token = getStoredToken();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// ─── Error Handling ───────────────────────────────────────────────────────────

/** Extracts a human-readable message from a backend error response. */
export const extractErrorMessage = (error: unknown): string => {
    if (axios.isAxiosError(error)) {
        const axiosError = error as AxiosError<{ error?: string; errors?: Record<string, string> }>;
        const data = axiosError.response?.data;
        if (data?.error) return data.error;
        if (data?.errors) return Object.values(data.errors).join(' ');
        if (axiosError.response?.status === 401) return 'Invalid email or password.';
        if (axiosError.response?.status === 409) return 'An account with this email already exists.';
        if (axiosError.response?.status === 403) return 'You do not have permission to do that.';
        if (axiosError.response?.status === 0 || !axiosError.response) return 'Cannot connect to server. Please ensure the backend is running.';
    }
    return 'An unexpected error occurred. Please try again.';
};

// ─── Types (matching backend DTOs) ───────────────────────────────────────────

export interface TravelPersonaData {
    id?: string;
    travelStyles: string[];
    interests: string[];
    travelFrequency: string;
    preferredPace: string;
}

export interface UserData {
    id: string;
    email: string;
    name: string;
    avatar?: string;
    travelPersonas?: TravelPersonaData[];
}

export interface AuthResponseData {
    token: string;
    expiresAt: number;
    user: UserData;
}

// ─── Auth API ─────────────────────────────────────────────────────────────────

export const authApi = {
    login: async (email: string, password: string, recaptchaToken: string): Promise<AuthResponseData> => {
        const response = await api.post<AuthResponseData>('/auth/login', {
            email,
            password,
            recaptchaToken,
        });
        storeToken(response.data.token);
        return response.data;
    },

    register: async (name: string, email: string, password: string, recaptchaToken: string): Promise<AuthResponseData> => {
        const response = await api.post<AuthResponseData>('/auth/register', {
            name,
            email,
            password,
            recaptchaToken,
        });
        storeToken(response.data.token);
        return response.data;
    },

    logout: (): void => {
        removeToken();
    },
};

// ─── User API ─────────────────────────────────────────────────────────────────

export const userApi = {
    getMe: async (): Promise<UserData> => {
        const response = await api.get<UserData>('/users/me');
        return response.data;
    },

    updateProfile: async (name: string, avatar?: string | null): Promise<UserData> => {
        const response = await api.put<UserData>('/users/me', { name, avatar: avatar ?? null });
        return response.data;
    },

    changePassword: async (oldPassword: string, newPassword: string): Promise<void> => {
        await api.put('/users/me/password', { oldPassword, newPassword });
    },

    deleteAccount: async (): Promise<void> => {
        await api.delete('/users/me');
        removeToken();
    },

    // --- New Endpoint Bindings ---

    getAllUsers: async (): Promise<UserData[]> => {
        const response = await api.get<UserData[]>('/users');
        return response.data;
    },

    getUserById: async (userId: string): Promise<UserData> => {
        const response = await api.get<UserData>(`/users/${userId}`);
        return response.data;
    },

    searchUserByName: async (name: string): Promise<UserData> => {
        const response = await api.get<UserData>(`/users/search?name=${name}`);
        return response.data;
    },

    updateName: async (userId: string, name: string): Promise<void> => {
        await api.put(`/users/${userId}/name`, name, { headers: { 'Content-Type': 'text/plain' } });
    },

    updateEmail: async (userId: string, email: string): Promise<void> => {
        await api.put(`/users/${userId}/email`, email, { headers: { 'Content-Type': 'text/plain' } });
    },

    addPlaceToPlan: async (userId: string, placeId: string | number): Promise<void> => {
        await api.post(`/users/${userId}/places`, placeId, { headers: { 'Content-Type': 'application/json' } });
    },

    // ----------------------------

    getPersonas: async (): Promise<TravelPersonaData[]> => {
        const response = await api.get<TravelPersonaData[]>('/users/me/personas');
        return response.data;
    },

    createPersona: async (persona: Omit<TravelPersonaData, 'id'>): Promise<TravelPersonaData> => {
        const response = await api.post<TravelPersonaData>('/users/me/personas/new', persona);
        return response.data;
    },

    updatePersona: async (personaId: string, persona: Omit<TravelPersonaData, 'id'>): Promise<TravelPersonaData> => {
        const response = await api.put<TravelPersonaData>(`/users/me/personas/${personaId}`, persona);
        return response.data;
    },

    deletePersona: async (personaId: string): Promise<void> => {
        await api.delete(`/users/me/personas/${personaId}`);
    },
};

// ─── Chat/Message Types ───────────────────────────────────────────────────────

export interface MessageData {
    id: string;
    role: string;
    content: string;
    timestamp: number;
}

export interface ChatData {
    id: string;
    title: string;
    duration?: string;
    createdAt: number;
    updatedAt: number;
    messages: MessageData[];
}

// ─── Chat API ─────────────────────────────────────────────────────────────────

export const chatApi = {
    getAll: async (): Promise<ChatData[]> => {
        const response = await api.get<ChatData[]>('/chats');
        return response.data;
    },

    create: async (title: string, duration?: string): Promise<ChatData> => {
        const response = await api.post<ChatData>('/chats/new', { title, duration });
        return response.data;
    },

    getById: async (chatId: string): Promise<ChatData> => {
        const response = await api.get<ChatData>(`/chats/${chatId}`);
        return response.data;
    },

    delete: async (chatId: string): Promise<void> => {
        await api.delete(`/chats/${chatId}`);
    },

    updateTitle: async (chatId: string, title: string): Promise<ChatData> => {
        const response = await api.put<ChatData>(`/chats/${chatId}/title`, { title });
        return response.data;
    },

    addMessage: async (chatId: string, role: string, content: string): Promise<ChatData> => {
        const response = await api.post<ChatData>(`/chats/${chatId}/messages`, { role, content });
        return response.data;
    },
};

