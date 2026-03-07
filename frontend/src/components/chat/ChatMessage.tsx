import { Box, Typography, Avatar, Sheet, IconButton } from '@mui/joy';
import StarIcon from '@mui/icons-material/Star';
import AddIcon from '@mui/icons-material/Add';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Message, LocationCard } from '../../store/chatSlice';

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
                    color="primary"
                    sx={{
                        px: 2,
                        py: 1.5,
                        borderRadius: 'lg',
                        borderBottomRightRadius: 'sm',
                        maxWidth: '80%',
                    }}
                >
                    <Typography level="body-md" sx={{ color: '#fff' }}>
                        {message.content}
                    </Typography>
                </Sheet>
            </Box>
        );
    }

    // AI message - left aligned with icon and markdown support
    return (
        <Box sx={{ display: 'flex', gap: 1.5, mb: 2 }}>
            <Avatar
                size="sm"
                sx={{
                    bgcolor: 'background.level2',
                    flexShrink: 0,
                }}
            >
                <AutoAwesomeIcon sx={{ fontSize: 18 }} />
            </Avatar>
            <Box sx={{ flex: 1, minWidth: 0 }}>
                <MarkdownContent content={message.content} />
                {message.locationCard && (
                    <LocationCardComponent location={message.locationCard} />
                )}
            </Box>
        </Box>
    );
};

export default ChatMessage;
