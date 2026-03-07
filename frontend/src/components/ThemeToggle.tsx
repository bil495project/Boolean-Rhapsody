import { IconButton } from '@mui/joy';
import { useColorScheme } from '@mui/joy/styles';
import LightModeIcon from '@mui/icons-material/LightMode';
import DarkModeIcon from '@mui/icons-material/DarkMode';

interface ThemeToggleProps {
    size?: 'sm' | 'md' | 'lg';
    variant?: 'plain' | 'outlined' | 'soft' | 'solid';
}

const ThemeToggle = ({ size = 'md', variant = 'plain' }: ThemeToggleProps) => {
    const { mode, setMode } = useColorScheme();

    const toggleTheme = () => {
        setMode(mode === 'light' ? 'dark' : 'light');
    };

    return (
        <IconButton
            variant={variant}
            size={size}
            onClick={toggleTheme}
            aria-label={mode === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}
            sx={{
                transition: 'all 0.2s ease-in-out',
                '&:hover': {
                    transform: 'rotate(15deg)',
                },
            }}
        >
            {mode === 'light' ? <DarkModeIcon /> : <LightModeIcon />}
        </IconButton>
    );
};

export default ThemeToggle;
