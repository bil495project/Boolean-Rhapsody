import { useState, useRef, useEffect } from 'react';
import {
    Box,
    Typography,
    Input,
    IconButton,
    Chip,
    CircularProgress,
} from '@mui/joy';
import SendIcon from '@mui/icons-material/Send';
import AddIcon from '@mui/icons-material/Add';
import MicIcon from '@mui/icons-material/Mic';
import MenuIcon from '@mui/icons-material/Menu';
import ChatMessage from './ChatMessage';
import { useAppSelector, useAppDispatch } from '../../store/hooks';
import { addMessageLocal, addMessageAsync, setLoading, toggleSidebar } from '../../store/chatSlice';
import { toggleSaveDestination, syncToggleToBackend } from '../../store/savedSlice';
import { sendMessage, type ChatMessage as GeminiMessage, type ToolCallResult } from '../../services/geminiService';

interface ChatPanelProps {
    userName: string;
    onMenuClick?: () => void;
    showMenuButton?: boolean;
    isNewChatMode?: boolean;
    onCreateChat?: (firstMessage: string) => void;
}

const ChatPanel = ({
    userName,
    onMenuClick,
    showMenuButton = false,
    isNewChatMode = false,
    onCreateChat
}: ChatPanelProps) => {
    const [inputValue, setInputValue] = useState('');
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const dispatch = useAppDispatch();
    const { activeChat, isLoading, sidebarOpen } = useAppSelector((state) => state.chat);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    useEffect(() => {
        scrollToBottom();
    }, [activeChat?.messages]);

    const handleSend = async () => {
        if (!inputValue.trim() || isLoading) return;

        const userMessage = inputValue.trim();
        setInputValue('');

        // If in new chat mode, create the chat first
        if (isNewChatMode && onCreateChat) {
            onCreateChat(userMessage);
            return;
        }

        // Normal flow - add message to existing chat
        if (!activeChat) return;

        // Add user message optimistically
        dispatch(addMessageLocal({
            chatId: activeChat.id,
            message: {
                id: `temp-${Date.now()}`,
                role: 'user',
                content: userMessage,
                timestamp: Date.now(),
            },
        }));

        // Persist user message to backend
        await dispatch(addMessageAsync({ chatId: activeChat.id, role: 'user', content: userMessage }));

        dispatch(setLoading(true));

        try {
            // Build chat history for context
            const history: GeminiMessage[] = activeChat.messages.map((msg) => ({
                role: msg.role === 'user' ? 'user' : 'model',
                content: msg.content,
            }));

            // Get AI response (now returns ToolCallResult)
            const response: ToolCallResult = await sendMessage(userMessage, history);

            // If AI saved a destination via tool call, add it to savedSlice and sync
            if (response.type === 'destination_saved' && response.savedDestination) {
                dispatch(toggleSaveDestination(response.savedDestination));
                dispatch(syncToggleToBackend(response.savedDestination));
            }

            // Add AI response message to backend
            await dispatch(addMessageAsync({
                chatId: activeChat.id,
                role: 'assistant',
                content: response.message,
            }));
        } catch (error) {
            console.error('Chat error:', error);
            await dispatch(addMessageAsync({
                chatId: activeChat.id,
                role: 'assistant',
                content: `Sorry, I encountered an error. Please try again. (${error instanceof Error ? error.message : 'Unknown error'})`,
            }));
        } finally {
            dispatch(setLoading(false));
        }
    };

    const handleKeyPress = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    // Determine what to show in header
    const headerTitle = isNewChatMode ? 'New Trip' : (activeChat?.title || 'New Trip');
    const hasMessages = !isNewChatMode && activeChat?.messages && activeChat.messages.length > 0;

    return (
        <Box
            sx={{
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                bgcolor: 'background.body',
            }}
        >
            {/* Header */}
            <Box
                sx={{
                    p: 2,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 2,
                    borderBottom: '1px solid',
                    borderColor: 'divider',
                }}
            >
                {/* Show menu button: either for mobile (showMenuButton) or desktop when sidebar closed */}
                {(showMenuButton || !sidebarOpen) && (
                    <IconButton
                        variant="plain"
                        size="sm"
                        onClick={showMenuButton && onMenuClick ? onMenuClick : () => dispatch(toggleSidebar())}
                    >
                        <MenuIcon />
                    </IconButton>
                )}
                <Typography level="h4" sx={{ flex: 1, fontWeight: 600 }}>
                    {headerTitle}
                </Typography>
                {activeChat?.duration && (
                    <Chip variant="outlined" size="sm">
                        {activeChat.duration}
                    </Chip>
                )}
            </Box>

            {/* Messages Area */}
            <Box
                sx={{
                    flex: 1,
                    overflow: 'auto',
                    p: 3,
                }}
            >
                {/* Welcome Message - show in new mode or when no messages */}
                {!hasMessages && (
                    <Box sx={{ textAlign: 'center', py: 6 }}>
                        <Box
                            sx={{
                                width: 80,
                                height: 80,
                                borderRadius: '20px',
                                bgcolor: 'background.level2',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                mx: 'auto',
                                mb: 3,
                                fontSize: '2rem',
                            }}
                        >
                            🗺️
                        </Box>
                        <Typography level="h3" sx={{ mb: 1 }}>
                            What's the plan, {userName}?
                        </Typography>
                        <Typography level="body-md" sx={{ color: 'text.secondary' }}>
                            I'm ready to help you plan your next adventure.
                        </Typography>
                    </Box>
                )}

                {/* Messages */}
                {!isNewChatMode && activeChat?.messages.map((message) => (
                    <ChatMessage key={message.id} message={message} />
                ))}

                {/* Loading indicator */}
                {isLoading && (
                    <Box sx={{ display: 'flex', gap: 1.5, mb: 2 }}>
                        <Box
                            sx={{
                                width: 32,
                                height: 32,
                                borderRadius: '50%',
                                bgcolor: 'background.level2',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                            }}
                        >
                            <CircularProgress size="sm" />
                        </Box>
                        <Typography level="body-md" sx={{ color: 'text.secondary' }}>
                            Thinking...
                        </Typography>
                    </Box>
                )}

                <div ref={messagesEndRef} />
            </Box>

            {/* Input Area */}
            <Box
                sx={{
                    p: 2,
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 0.5,
                        bgcolor: 'background.level1',
                        borderRadius: 'xl',
                        py: 0.75,
                        px: 1,
                    }}
                >
                    {/* TODO: Implement attachment functionality */}
                    <IconButton variant="plain" size="sm" disabled>
                        <AddIcon />
                    </IconButton>

                    <Input
                        placeholder="Ask me anything about your trip..."
                        value={inputValue}
                        onChange={(e) => setInputValue(e.target.value)}
                        onKeyPress={handleKeyPress}
                        disabled={isLoading}
                        variant="plain"
                        sx={{
                            flex: 1,
                            '--Input-focusedThickness': '0',
                            '--Input-placeholderColor': 'var(--joy-palette-text-tertiary)',
                            bgcolor: 'transparent',
                            '&::before': { display: 'none' },
                            '&:focus-within': {
                                outline: 'none',
                                boxShadow: 'none',
                            },
                        }}
                    />

                    {/* TODO: Implement voice input functionality */}
                    <IconButton variant="plain" size="sm" disabled>
                        <MicIcon />
                    </IconButton>

                    <IconButton
                        variant="solid"
                        color="primary"
                        size="sm"
                        onClick={handleSend}
                        disabled={!inputValue.trim() || isLoading}
                        sx={{ borderRadius: '50%' }}
                    >
                        <SendIcon />
                    </IconButton>
                </Box>
            </Box>
        </Box>
    );
};

export default ChatPanel;
