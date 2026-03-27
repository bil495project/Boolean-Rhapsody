import { Box } from '@mui/joy';
import { useColorScheme } from '@mui/joy/styles';
import type { SxProps } from '@mui/system';
import roadrunnerBird from '../assets/roadrunnerbird.png';
import roadrunnerBirdWhite from '../assets/roadrunnerbird_white.png';

interface RoadrunnerBirdLogoProps {
    size?: number | string;
    alt?: string;
    sx?: SxProps;
}

const RoadrunnerBirdLogo = ({
    size = 24,
    alt = 'Roadrunner logo',
    sx,
}: RoadrunnerBirdLogoProps) => {
    const { mode } = useColorScheme();

    return (
        <Box
            component="img"
            src={mode === 'dark' ? roadrunnerBirdWhite : roadrunnerBird}
            alt={alt}
            sx={{
                width: size,
                height: size,
                objectFit: 'contain',
                flexShrink: 0,
                display: 'block',
                ...sx,
            }}
        />
    );
};

export default RoadrunnerBirdLogo;
