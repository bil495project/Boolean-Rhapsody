import { Box, Typography, Link } from '@mui/joy';
import RoadrunnerBirdLogo from './RoadrunnerBirdLogo';

const Footer = () => {
    const currentYear = new Date().getFullYear();

    return (
        <Box
            component="footer"
            sx={{
                py: 3,
                px: { xs: 2, md: 4 },
                mt: 'auto',
                backgroundColor: 'background.surface',
                borderTop: '1px solid',
                borderColor: 'divider',
            }}
        >
            <Box
                sx={{
                    display: 'flex',
                    flexDirection: { xs: 'column', md: 'row' },
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: 2,
                    maxWidth: 1200,
                    mx: 'auto',
                }}
            >
                {/* Logo & Copyright */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <RoadrunnerBirdLogo size={20} />
                    <Typography level="body-sm" sx={{ color: 'text.secondary' }}>
                        Roadrunner © {currentYear}
                    </Typography>
                </Box>

                {/* Links */}
                <Box sx={{ display: 'flex', gap: 3 }}>
                    <Link
                        level="body-sm"
                        href="#"
                        sx={{
                            color: 'text.secondary',
                            textDecoration: 'none',
                            '&:hover': { color: 'primary.500' },
                        }}
                    >
                        Privacy Policy
                    </Link>
                    <Link
                        level="body-sm"
                        href="#"
                        sx={{
                            color: 'text.secondary',
                            textDecoration: 'none',
                            '&:hover': { color: 'primary.500' },
                        }}
                    >
                        Terms of Service
                    </Link>
                    <Link
                        level="body-sm"
                        href="#"
                        sx={{
                            color: 'text.secondary',
                            textDecoration: 'none',
                            '&:hover': { color: 'primary.500' },
                        }}
                    >
                        Contact
                    </Link>
                </Box>

                {/* AI Assistant text */}
                <Typography level="body-xs" sx={{ color: 'text.tertiary' }}>
                    Your AI-powered travel assistant
                </Typography>
            </Box>
        </Box>
    );
};

export default Footer;
