import { Box, Typography, Avatar, Sheet, IconButton } from '@mui/joy';
import StarIcon from '@mui/icons-material/Star';
import AddIcon from '@mui/icons-material/Add';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Message, LocationCard } from '../../store/chatSlice';
import RoadrunnerBirdLogo from '../RoadrunnerBirdLogo';

interface ChatMessageProps {
    message: Message;
}

const LocationCardComponent = ({ location }: { location: LocationCard }) => {
    return (
        <Sheet
            variant="outlined"
            sx={{
                p: 1.5,
                borderRadius: 'lg',
                display: 'flex',
                alignItems: 'center',
                gap: 2,
                mt: 1.5,
                cursor: 'pointer',
                transition: 'all 0.2s',
                '&:hover': {
                    borderColor: 'primary.500',
                    boxShadow: 'sm',
                },
            }}
        >
            {location.image && (
                <Box
                    component="img"
                    src={location.image}
                    alt={location.name}
                    sx={{
                        width: 60,
                        height: 60,
                        borderRadius: 'md',
                        objectFit: 'cover',
                    }}
                />
            )}
            <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography level="body-md" sx={{ fontWeight: 600 }}>
                    {location.name}
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <StarIcon sx={{ fontSize: 14, color: '#FFB800' }} />
                    <Typography level="body-xs">
                        {location.rating} · {location.type}
                    </Typography>
                </Box>
            </Box>
            <IconButton variant="plain" size="sm">
                <AddIcon />
            </IconButton>
        </Sheet>
    );
};

// Markdown components styling
const MarkdownContent = ({ content }: { content: string }) => {
    return (
        <Box
            sx={{
                '& p': {
                    m: 0,
                    mb: 1.5,
                    '&:last-child': { mb: 0 },
                },
                '& h1, & h2, & h3, & h4, & h5, & h6': {
                    mt: 2,
                    mb: 1,
                    fontWeight: 600,
                    '&:first-of-type': { mt: 0 },
                },
                '& h1': { fontSize: '1.5rem' },
                '& h2': { fontSize: '1.3rem' },
                '& h3': { fontSize: '1.1rem' },
                '& ul, & ol': {
                    m: 0,
                    mb: 1.5,
                    pl: 2.5,
                },
                '& li': {
                    mb: 0.5,
                },
                '& table': {
                    width: '100%',
                    borderCollapse: 'collapse',
                    mb: 1.5,
                    fontSize: '0.875rem',
                },
                '& th, & td': {
                    border: '1px solid',
                    borderColor: 'divider',
                    px: 1.5,
                    py: 1,
                    textAlign: 'left',
                },
                '& th': {
                    bgcolor: 'background.level1',
                    fontWeight: 600,
                },
                '& code': {
                    bgcolor: 'background.level2',
                    px: 0.75,
                    py: 0.25,
                    borderRadius: 'sm',
                    fontSize: '0.85em',
                    fontFamily: 'monospace',
                },
                '& pre': {
                    bgcolor: 'background.level2',
                    p: 1.5,
                    borderRadius: 'md',
                    overflow: 'auto',
                    mb: 1.5,
                    '& code': {
                        bgcolor: 'transparent',
                        p: 0,
                    },
                },
                '& blockquote': {
                    borderLeft: '3px solid',
                    borderColor: 'primary.500',
                    pl: 2,
                    ml: 0,
                    my: 1.5,
                    color: 'text.secondary',
                },
                '& a': {
                    color: 'primary.500',
                    textDecoration: 'none',
                    '&:hover': {
                        textDecoration: 'underline',
                    },
                },
                '& strong': {
                    fontWeight: 600,
                },
                '& hr': {
                    border: 'none',
                    borderTop: '1px solid',
                    borderColor: 'divider',
                    my: 2,
                },
            }}
        >
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {content}
            </ReactMarkdown>
        </Box>
    );
};

const ChatMessage = ({ message }: ChatMessageProps) => {
    const isUser = message.role === 'user';

    if (isUser) {
        // User message - right aligned, turquoise bubble
        return (
            <Box
                sx={{
                    display: 'flex',
                    justifyContent: 'flex-end',
                    mb: 2,
                }}
            >
                <Sheet
                    variant="solid"
                    sx={{
                        px: 2,
                        py: 1.5,
                        borderRadius: '20px',
                        borderBottomRightRadius: '4px',
                        maxWidth: '80%',
                        bgcolor: '#00b894', // Premium Teal
                        boxShadow: '0 4px 15px rgba(0, 184, 148, 0.15)',
                    }}
                >
                    <Typography level="body-md" sx={{ color: '#fff', fontWeight: 500 }}>
                        {message.content}
                    </Typography>
                </Sheet>
            </Box>
        );
    }

    // AI message - left-aligned
    return (
        <Box sx={{ display: 'flex', gap: 1.5, mb: 4 }}>
            <Avatar
                size="sm"
                sx={{
                    bgcolor: 'background.level2',
                    flexShrink: 0,
                    boxShadow: 'sm',
                    border: '1px solid',
                    borderColor: 'divider',
                }}
            >
                <RoadrunnerBirdLogo size={22} />
            </Avatar>
            <Box sx={{ flex: 1, minWidth: 0 }}>
                <MarkdownContent content={message.content} />
                
                {message.toolUsed && (
                    <Box
                        sx={{
                            mt: 2,
                            p: 2,
                            bgcolor: (theme) => 
                                theme.vars.palette.mode === 'dark' 
                                    ? 'rgba(15, 23, 42, 0.4)' // Deep slate
                                    : 'background.level1',
                            borderRadius: '16px',
                            border: '1px solid',
                            borderColor: (theme) =>
                                theme.vars.palette.mode === 'dark'
                                    ? 'rgba(51, 65, 85, 0.5)' 
                                    : 'divider',
                            position: 'relative',
                            overflow: 'hidden',
                        }}
                    >
                        {/* Status line matching User's latest screenshot */}
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                            <Box 
                                sx={{ 
                                    width: 4, 
                                    height: 16, 
                                    bgcolor: 'primary.500', 
                                    borderRadius: 'full',
                                    opacity: 0.8 
                                }} 
                            />
                            <Typography 
                                level="body-sm" 
                                sx={{ 
                                    color: (theme) =>
                                        theme.vars.palette.mode === 'dark'
                                            ? 'text.secondary'
                                            : 'text.primary',
                                    fontWeight: 600,
                                    letterSpacing: '0.02em'
                                }}
                            >
                                Tool call: <Box component="span" sx={{ color: 'primary.400', fontFamily: 'monospace', ml: 0.5 }}>{message.toolUsed}</Box>
                            </Typography>
                        </Box>

                        {message.toolParams && (
                            <Box 
                                sx={{ 
                                    mt: 2,
                                    p: 1.5,
                                    bgcolor: 'rgba(0, 0, 0, 0.2)', 
                                    borderRadius: '10px',
                                    border: '1px solid rgba(255, 255, 255, 0.05)',
                                }}
                            >
                                <Typography
                                    component="pre"
                                    level="body-xs"
                                    sx={{
                                        fontFamily: '"Fira Code", "JetBrains Mono", monospace',
                                        whiteSpace: 'pre-wrap',
                                        wordBreak: 'break-all',
                                        color: '#94a3b8', 
                                        fontSize: '0.75rem',
                                        lineHeight: 1.6,
                                    }}
                                >
                                    {typeof message.toolParams === 'string' 
                                        ? message.toolParams 
                                        : JSON.stringify(message.toolParams, null, 2)}
                                </Typography>
                            </Box>
                        )}
                    </Box>
                )}

                {message.locationCard && (
                    <LocationCardComponent location={message.locationCard} />
                )}
            </Box>
        </Box>
    );
};

export default ChatMessage;
