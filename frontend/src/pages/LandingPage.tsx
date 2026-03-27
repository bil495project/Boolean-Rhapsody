import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    Box,
    Typography,
    Input,
    Chip,
    IconButton,
} from '@mui/joy';
import { useColorScheme } from '@mui/joy/styles';
import MuseumIcon from '@mui/icons-material/Museum';
import ParkIcon from '@mui/icons-material/Park';
import LandscapeIcon from '@mui/icons-material/Landscape';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import Header from '../components/Header';
import Footer from '../components/Footer';
import RoadrunnerBirdLogo from '../components/RoadrunnerBirdLogo';

const beachBgUrl = 'https://images.unsplash.com/photo-1651757621103-122c41860956?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHx0cmF2ZWwlMjBiZWFjaCUyMG9jZWFuJTIwYWVyaWFsfGVufDF8fHx8MTc2NDYxNDIwMnww&ixlib=rb-4.1.0&q=80&w=1920';

const LandingPage = () => {
    const [searchQuery, setSearchQuery] = useState('');
    const navigate = useNavigate();
    const { mode } = useColorScheme();

    const quickSuggestions = [
        {
            label: 'Historical Sites',
            icon: <AccountBalanceIcon sx={{ fontSize: 16 }} />,
            color: '#FF6B6B',
            prompt: 'Show me historical sites, monuments, and heritage locations in Ankara. Include visiting tips, best times to visit, and historical significance of each place.'
        },
        {
            label: 'Museums',
            icon: <MuseumIcon sx={{ fontSize: 16 }} />,
            color: '#9B59B6',
            prompt: 'Recommend museums and art galleries in Ankara. Include their collections, opening hours, ticket prices, and what makes each one unique.'
        },
        {
            label: 'Nature & Parks',
            icon: <LandscapeIcon sx={{ fontSize: 16 }} />,
            color: '#4DD4C3',
            prompt: 'Find nature spots, parks, landmarks and outdoor activities in Ankara. Include scenic views, hiking trails, and relaxation spots.'
        },
        {
            label: 'Cultural Experiences',
            icon: <ParkIcon sx={{ fontSize: 16 }} />,
            color: '#4ECDC4',
            prompt: 'Suggest cultural experiences, local traditions, authentic neighborhoods, and traditional spots in Ankara. Include local food, crafts, and cultural events.'
        },
    ];

    const handleSearch = () => {
        if (searchQuery.trim()) {
            const chatId = `chat-${Date.now()}`;
            navigate(`/chat/${chatId}?q=${encodeURIComponent(searchQuery)}`);
        }
    };

    const handleSuggestionClick = (prompt: string) => {
        const chatId = `chat-${Date.now()}`;
        navigate(`/chat/${chatId}?q=${encodeURIComponent(prompt)}`);
    };

    return (
        <Box
            sx={{
                minHeight: '100vh',
                display: 'flex',
                flexDirection: 'column',
                position: 'relative',
                overflow: 'hidden',
            }}
        >
            {/* Background Image */}
            <Box
                sx={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundImage: `url(${beachBgUrl})`,
                    backgroundSize: 'cover',
                    backgroundPosition: 'center bottom',
                    '&::after': {
                        content: '""',
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        background: 'linear-gradient(180deg, rgba(0,0,0,0.3) 0%, rgba(0,0,0,0.1) 40%, rgba(0,0,0,0.2) 100%)',
                    },
                }}
            />

            <Header />

            <Box
                sx={{
                    flex: 1,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'center',
                    position: 'relative',
                    zIndex: 1,
                    px: 3,
                    pt: 8,
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        maxWidth: 800,
                        textAlign: 'center',
                    }}
                >
                    <Typography
                        level="h1"
                        sx={{
                            color: '#fff',
                            fontWeight: 700,
                            fontSize: { xs: '2.5rem', sm: '3rem', md: '3.5rem' },
                            textShadow: '0 4px 12px rgba(0,0,0,0.4)',
                            mb: 2,
                            lineHeight: 1.2,
                        }}
                    >
                        Where would you like to go?
                    </Typography>

                    <Typography
                        level="body-lg"
                        sx={{
                            color: 'rgba(255,255,255,0.95)',
                            textShadow: '0 2px 8px rgba(0,0,0,0.4)',
                            mb: 4,
                            maxWidth: 500,
                        }}
                    >
                        Plan your dream vacation in seconds. Just tell us where you want to go
                        and what you love.
                    </Typography>

                    <Box
                        sx={{
                            width: '100%',
                            maxWidth: 600,
                            mb: 4,
                        }}
                    >
                        <Input
                            size="lg"
                            placeholder="Plan a trip to Ankara..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                            startDecorator={
                                <RoadrunnerBirdLogo size={20} sx={{ ml: 0.5 }} />
                            }
                            endDecorator={
                                <IconButton
                                    variant="solid"
                                    color="primary"
                                    onClick={handleSearch}
                                    sx={{
                                        borderRadius: '10px',
                                        mr: -0.5,
                                    }}
                                >
                                    <ArrowForwardIcon />
                                </IconButton>
                            }
                            sx={{
                                py: 1.5,
                                px: 2,
                                backgroundColor: 'rgba(255,255,255,0.95)',
                                backdropFilter: 'blur(10px)',
                                borderRadius: '16px',
                                boxShadow: '0 8px 32px rgba(0,0,0,0.2)',
                                border: 'none',
                                fontSize: '1rem',
                                '&:focus-within': {
                                    boxShadow: '0 8px 40px rgba(0,191,166,0.3)',
                                },
                                '& input': {
                                    color: '#1A1A1A',
                                    '&::placeholder': {
                                        color: '#8A8A8A',
                                    },
                                },
                            }}
                        />
                    </Box>

                    <Box
                        sx={{
                            display: 'flex',
                            flexWrap: 'wrap',
                            gap: 2,
                            justifyContent: 'center',
                            px: 2,
                        }}
                    >
                        {quickSuggestions.map((suggestion) => (
                            <Chip
                                key={suggestion.label}
                                variant="soft"
                                startDecorator={suggestion.icon}
                                onClick={() => handleSuggestionClick(suggestion.prompt)}
                                sx={{
                                    backgroundColor: mode === 'light' ? 'rgba(255,255,255,0.95)' : 'rgba(255,255,255,0.2)',
                                    backdropFilter: 'blur(10px)',
                                    border: mode === 'light' ? '1px solid rgba(0,0,0,0.1)' : '1px solid rgba(255,255,255,0.3)',
                                    color: mode === 'light' ? 'text.primary' : '#fff',
                                    fontWeight: 600,
                                    py: 1,
                                    px: 2,
                                    cursor: 'pointer',
                                    transition: 'all 0.2s ease',
                                    boxShadow: mode === 'light' ? '0 2px 8px rgba(0,0,0,0.1)' : 'none',
                                    '&:hover': {
                                        backgroundColor: mode === 'light' ? '#fff' : 'rgba(255,255,255,0.3)',
                                        transform: 'translateY(-2px)',
                                        boxShadow: mode === 'light' ? '0 4px 12px rgba(0,0,0,0.15)' : '0 4px 12px rgba(0,0,0,0.3)',
                                    },
                                    '& .MuiChip-startDecorator': {
                                        color: suggestion.color,
                                    },
                                }}
                            >
                                {suggestion.label}
                            </Chip>
                        ))}
                    </Box>
                </Box>
            </Box>

            <Box
                sx={{
                    position: 'relative',
                    zIndex: 1,
                    '& footer': {
                        backgroundColor: 'transparent',
                        borderTop: 'none',
                        '& *': {
                            color: 'rgba(255,255,255,0.8)',
                            textShadow: '0 1px 4px rgba(0,0,0,0.3)',
                        },
                        '& a:hover': {
                            color: '#fff',
                        },
                    },
                }}
            >
                <Footer />
            </Box>
        </Box>
    );
};

export default LandingPage;
