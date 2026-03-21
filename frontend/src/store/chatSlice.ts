import { createSlice, createAsyncThunk, type PayloadAction } from '@reduxjs/toolkit';
import { chatApi, type ChatData, type MessageData } from '../services/userService';

// ─── Types ────────────────────────────────────────────────────────────────────

export interface Message {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    toolUsed?: string;
    toolParams?: any;
    timestamp: number;
    locationCard?: LocationCard;
}

export interface LocationCard {
    name: string;
    type: string;
    rating: number;
    priceLevel: string;
    image?: string;
    coordinates?: { lat: number; lng: number };
}

export interface Chat {
    id: string;
    title: string;
    duration?: string;
    messages: Message[];
    createdAt: number;
    updatedAt: number;
}

interface ChatState {
    chats: Chat[];
    activeChat: Chat | null;
    isLoading: boolean;
    error: string | null;
    sidebarOpen: boolean;
    mapFullscreen: boolean;
    chatPanelWidth: number;
    exploreMode: boolean;
    savedMode: boolean;
    savedDestinations: string[];
}

// ─── Helpers: map backend data → frontend shape ──────────────────────────────

const mapMessageData = (m: MessageData): Message => ({
    id: m.id,
    role: m.role as 'user' | 'assistant',
    content: m.content,
    toolUsed: m.toolUsed,
    toolParams: m.toolParams ? JSON.parse(m.toolParams) : undefined,
    timestamp: m.timestamp,
});

const mapChatData = (c: ChatData): Chat => ({
    id: c.id,
    title: c.title,
    duration: c.duration,
    messages: (c.messages ?? []).map(mapMessageData),
    createdAt: c.createdAt,
    updatedAt: c.updatedAt,
});

// ─── Async Thunks ─────────────────────────────────────────────────────────────

export const fetchChats = createAsyncThunk('chat/fetchChats', async () => {
    const data = await chatApi.getAll();
    return data.map(mapChatData);
});

export const createChatAsync = createAsyncThunk(
    'chat/createChatAsync',
    async ({ title, duration }: { title: string; duration?: string }) => {
        const data = await chatApi.create(title, duration);
        return mapChatData(data);
    }
);

export const deleteChatAsync = createAsyncThunk(
    'chat/deleteChatAsync',
    async (chatId: string) => {
        console.log('Dispatching deleteChatAsync for ID:', chatId);
        await chatApi.delete(chatId);
        return chatId;
    }
);

export const updateChatTitleAsync = createAsyncThunk(
    'chat/updateChatTitleAsync',
    async ({ chatId, title }: { chatId: string; title: string }) => {
        const data = await chatApi.updateTitle(chatId, title);
        return mapChatData(data);
    }
);

export const addMessageAsync = createAsyncThunk(
    'chat/addMessageAsync',
    async ({ chatId, role, content, toolUsed, toolParams }: { chatId: string; role: string; content: string; toolUsed?: string; toolParams?: any }) => {
        const toolParamsStr = toolParams ? JSON.stringify(toolParams) : undefined;
        const data = await chatApi.addMessage(chatId, role, content, toolUsed, toolParamsStr);
        return mapChatData(data);
    }
);

// ─── Slice ────────────────────────────────────────────────────────────────────

const initialState: ChatState = {
    chats: [],
    activeChat: null,
    isLoading: false,
    error: null,
    sidebarOpen: true,
    mapFullscreen: false,
    chatPanelWidth: 50,
    exploreMode: false,
    savedMode: false,
    savedDestinations: JSON.parse(localStorage.getItem('travelplanner_saved_destinations') || '[]'),
};

const chatSlice = createSlice({
    name: 'chat',
    initialState,
    reducers: {
        setActiveChat: (state, action: PayloadAction<string>) => {
            const chat = state.chats.find((c) => c.id === action.payload);
            if (chat) {
                state.activeChat = chat;
            }
        },

        // Optimistic local add (used to show user messages immediately before API response)
        addMessageLocal: (state, action: PayloadAction<{ chatId: string; message: Message }>) => {
            const chat = state.chats.find((c) => c.id === action.payload.chatId);
            if (chat) {
                chat.messages.push(action.payload.message);
                chat.updatedAt = Date.now();
                if (state.activeChat?.id === action.payload.chatId) {
                    state.activeChat = chat;
                }
            }
        },

        setLoading: (state, action: PayloadAction<boolean>) => {
            state.isLoading = action.payload;
        },

        setError: (state, action: PayloadAction<string | null>) => {
            state.error = action.payload;
        },

        toggleSidebar: (state) => {
            state.sidebarOpen = !state.sidebarOpen;
        },

        setSidebarOpen: (state, action: PayloadAction<boolean>) => {
            state.sidebarOpen = action.payload;
        },

        toggleMapFullscreen: (state) => {
            state.mapFullscreen = !state.mapFullscreen;
        },

        setChatPanelWidth: (state, action: PayloadAction<number>) => {
            state.chatPanelWidth = Math.max(30, Math.min(70, action.payload));
        },

        toggleExploreMode: (state) => {
            state.exploreMode = !state.exploreMode;
            if (state.exploreMode) state.savedMode = false;
        },

        setExploreMode: (state, action: PayloadAction<boolean>) => {
            state.exploreMode = action.payload;
            if (action.payload) state.savedMode = false;
        },

        toggleSavedMode: (state) => {
            state.savedMode = !state.savedMode;
            if (state.savedMode) state.exploreMode = false;
        },

        setSavedMode: (state, action: PayloadAction<boolean>) => {
            state.savedMode = action.payload;
            if (action.payload) state.exploreMode = false;
        },

        toggleSavedDestination: (state, action: PayloadAction<string>) => {
            const index = state.savedDestinations.indexOf(action.payload);
            if (index === -1) {
                state.savedDestinations.push(action.payload);
            } else {
                state.savedDestinations.splice(index, 1);
            }
            localStorage.setItem('travelplanner_saved_destinations', JSON.stringify(state.savedDestinations));
        },

        addSavedDestination: (state, action: PayloadAction<string>) => {
            if (!state.savedDestinations.includes(action.payload)) {
                state.savedDestinations.push(action.payload);
                localStorage.setItem('travelplanner_saved_destinations', JSON.stringify(state.savedDestinations));
            }
        },

        clearChats: (state) => {
            state.chats = [];
            state.activeChat = null;
        },
    },

    extraReducers: (builder) => {
        // fetchChats
        builder.addCase(fetchChats.fulfilled, (state, action) => {
            state.chats = action.payload;
            // Re-sync active chat if it still exists
            if (state.activeChat) {
                const match = state.chats.find((c) => c.id === state.activeChat!.id);
                state.activeChat = match ?? null;
            }
        });

        // createChatAsync
        builder.addCase(createChatAsync.fulfilled, (state, action) => {
            state.chats.unshift(action.payload);
            state.activeChat = action.payload;
        });

        // deleteChatAsync
        builder.addCase(deleteChatAsync.fulfilled, (state, action) => {
            state.chats = state.chats.filter((c) => c.id !== action.payload);
            if (state.activeChat?.id === action.payload) {
                state.activeChat = null;
            }
        });

        // updateChatTitleAsync
        builder.addCase(updateChatTitleAsync.fulfilled, (state, action) => {
            const idx = state.chats.findIndex((c) => c.id === action.payload.id);
            if (idx !== -1) {
                state.chats[idx] = action.payload;
            }
            if (state.activeChat?.id === action.payload.id) {
                state.activeChat = action.payload;
            }
        });

        // addMessageAsync
        builder.addCase(addMessageAsync.fulfilled, (state, action) => {
            const idx = state.chats.findIndex((c) => c.id === action.payload.id);
            if (idx !== -1) {
                state.chats[idx] = action.payload;
            }
            if (state.activeChat?.id === action.payload.id) {
                state.activeChat = action.payload;
            }
        });

        // Rejected Handlers for Diagnostics
        builder.addCase(fetchChats.rejected, (state, action) => {
            console.error('fetchChats failed:', action.error);
            state.error = action.error.message || 'Failed to fetch chats';
        });
        builder.addCase(createChatAsync.rejected, (state, action) => {
            console.error('createChatAsync failed:', action.error);
            state.error = action.error.message || 'Failed to create chat';
        });
        builder.addCase(deleteChatAsync.rejected, (state, action) => {
            console.error('deleteChatAsync failed:', action.error);
            state.error = action.error.message || 'Failed to delete chat';
        });
        builder.addCase(updateChatTitleAsync.rejected, (state, action) => {
            console.error('updateChatTitleAsync failed:', action.error);
            state.error = action.error.message || 'Failed to update chat title';
        });
        builder.addCase(addMessageAsync.rejected, (state, action) => {
            console.error('addMessageAsync failed:', action.error);
            state.error = action.error.message || 'Failed to add message';
        });
    },
});

export const {
    setActiveChat,
    addMessageLocal,
    setLoading,
    setError,
    toggleSidebar,
    setSidebarOpen,
    toggleMapFullscreen,
    setChatPanelWidth,
    toggleExploreMode,
    setExploreMode,
    toggleSavedMode,
    setSavedMode,
    toggleSavedDestination,
    addSavedDestination,
    clearChats,
} = chatSlice.actions;

export default chatSlice.reducer;
