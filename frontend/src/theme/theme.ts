import { extendTheme } from '@mui/joy/styles';

// TravelPlanner AI - Custom Turquoise Theme
const theme = extendTheme({
    colorSchemes: {
        light: {
            palette: {
                primary: {
                    50: '#E0F7F4',
                    100: '#B3ECE5',
                    200: '#80E0D4',
                    300: '#4DD4C3',
                    400: '#26CAB6',
                    500: '#00BFA6',
                    600: '#00A892',
                    700: '#00917C',
                    800: '#007A68',
                    900: '#005346',
                    solidBg: '#00BFA6',
                    solidHoverBg: '#009688',
                    solidActiveBg: '#00796B',
                    softBg: '#E0F7F4',
                    softHoverBg: '#B3ECE5',
                    softColor: '#00796B',
                    outlinedBorder: '#00BFA6',
                    outlinedColor: '#00BFA6',
                    outlinedHoverBg: '#E0F7F4',
                },
                background: {
                    body: '#FFFFFF',
                    surface: '#F5F7FA',
                    popup: '#FFFFFF',
                },
                text: {
                    primary: '#1A1A1A',
                    secondary: '#5A5A5A',
                    tertiary: '#8A8A8A',
                },
                neutral: {
                    50: '#F8FAFC',
                    100: '#F1F5F9',
                    200: '#E2E8F0',
                    300: '#CBD5E1',
                    400: '#94A3B8',
                    500: '#64748B',
                    600: '#475569',
                    700: '#334155',
                    800: '#1E293B',
                    900: '#0F172A',
                },
            },
        },
        dark: {
            palette: {
                primary: {
                    50: '#E0F7F4',
                    100: '#B3ECE5',
                    200: '#80E0D4',
                    300: '#4DD4C3',
                    400: '#26CAB6',
                    500: '#00BFA6',
                    600: '#00A892',
                    700: '#00917C',
                    800: '#007A68',
                    900: '#005346',
                    solidBg: '#00BFA6',
                    solidHoverBg: '#33CCBA',
                    solidActiveBg: '#4DD4C3',
                    softBg: 'rgba(0, 191, 166, 0.15)',
                    softHoverBg: 'rgba(0, 191, 166, 0.25)',
                    softColor: '#4DD4C3',
                    outlinedBorder: '#00BFA6',
                    outlinedColor: '#00BFA6',
                    outlinedHoverBg: 'rgba(0, 191, 166, 0.15)',
                },
                background: {
                    body: '#0D1B2A',
                    surface: '#1B2838',
                    popup: '#1B2838',
                },
                text: {
                    primary: '#FFFFFF',
                    secondary: '#B8C5D6',
                    tertiary: '#8899A8',
                },
                neutral: {
                    50: '#F8FAFC',
                    100: '#F1F5F9',
                    200: '#E2E8F0',
                    300: '#CBD5E1',
                    400: '#94A3B8',
                    500: '#64748B',
                    600: '#475569',
                    700: '#334155',
                    800: '#1E293B',
                    900: '#0F172A',
                },
            },
        },
    },
    fontFamily: {
        body: '"Figtree", sans-serif',
        display: '"Figtree", sans-serif',
    },
    typography: {
        h1: {
            fontFamily: '"Figtree", sans-serif',
            fontWeight: 700,
            fontSize: '3rem',
            lineHeight: 1.2,
            '@media (max-width: 600px)': {
                fontSize: '2rem',
            },
        },
        h2: {
            fontFamily: '"Figtree", sans-serif',
            fontWeight: 600,
            fontSize: '2.25rem',
            lineHeight: 1.3,
            '@media (max-width: 600px)': {
                fontSize: '1.75rem',
            },
        },
        h3: {
            fontFamily: '"Figtree", sans-serif',
            fontWeight: 600,
            fontSize: '1.5rem',
            lineHeight: 1.4,
        },
        h4: {
            fontFamily: '"Figtree", sans-serif',
            fontWeight: 600,
            fontSize: '1.25rem',
            lineHeight: 1.4,
        },
        'body-lg': {
            fontFamily: '"Figtree", sans-serif',
            fontSize: '1.125rem',
        },
        'body-md': {
            fontFamily: '"Figtree", sans-serif',
            fontSize: '1rem',
        },
        'body-sm': {
            fontFamily: '"Figtree", sans-serif',
            fontSize: '0.875rem',
        },
    },
    radius: {
        xs: '4px',
        sm: '8px',
        md: '12px',
        lg: '16px',
        xl: '24px',
    },
    components: {
        JoyButton: {
            styleOverrides: {
                root: {
                    fontFamily: '"Figtree", sans-serif',
                    fontWeight: 600,
                    borderRadius: '12px',
                    textTransform: 'none',
                    transition: 'all 0.2s ease-in-out',
                },
            },
        },
        JoyInput: {
            styleOverrides: {
                root: {
                    fontFamily: '"Figtree", sans-serif',
                    borderRadius: '12px',
                },
            },
        },
        JoyCard: {
            styleOverrides: {
                root: {
                    borderRadius: '16px',
                    transition: 'all 0.3s ease-in-out',
                },
            },
        },
        JoyChip: {
            styleOverrides: {
                root: {
                    fontFamily: '"Figtree", sans-serif',
                    borderRadius: '20px',
                },
            },
        },
        JoyLink: {
            styleOverrides: {
                root: {
                    fontFamily: '"Figtree", sans-serif',
                },
            },
        },
    },
});

export default theme;
