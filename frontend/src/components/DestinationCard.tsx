import { useState } from 'react';
import { Box, Card, Typography, AspectRatio, Chip } from '@mui/joy';
import StarIcon from '@mui/icons-material/Star';
import LocationOnIcon from '@mui/icons-material/LocationOn';

export interface Destination {
    id: string;
    name: string;
    location: string;
    image: string;
    rating: number;
    priceLevel: 1 | 2 | 3 | 4;
    category: string;
    description?: string;
    openingHours?: string;
    phone?: string;
    website?: string;
    reviewCount?: number;
    businessStatus?: string;
    types?: string[];
}

interface DestinationCardProps {
    destination: Destination;
    onClick?: () => void;
}

const DestinationCard = ({ destination, onClick }: DestinationCardProps) => {
    const priceLabel = '$'.repeat(destination.priceLevel);
    const [imgSrc, setImgSrc] = useState(destination.image);

    const handleImageError = () => {
        // Fallback to a reliable generic travel image if the specific one fails
        setImgSrc('https://images.unsplash.com/photo-1488085061387-422e29b40080?w=800');
    };

    return (
        <Card
            variant="outlined"
            onClick={onClick}
            sx={{
                cursor: onClick ? 'pointer' : 'default',
                overflow: 'hidden',
                transition: 'all 0.3s ease-in-out',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                '&:hover': {
                    transform: 'translateY(-8px)',
                    boxShadow: 'lg',
                    borderColor: 'primary.500',
                    '& .destination-image': {
                        transform: 'scale(1.05)',
                    },
                },
            }}
        >
            <AspectRatio ratio="4/3" sx={{ overflow: 'hidden', flexShrink: 0 }}>
                <Box
                    component="img"
                    className="destination-image"
                    src={imgSrc}
                    alt={destination.name}
                    onError={handleImageError}
                    sx={{
                        objectFit: 'cover',
                        transition: 'transform 0.4s ease-in-out',
                    }}
                />
            </AspectRatio>

            <Box sx={{ p: 2, flex: 1, display: 'flex', flexDirection: 'column' }}>
                {/* Category Chip */}
                <Chip
                    size="sm"
                    variant="soft"
                    color="primary"
                    sx={{ mb: 1, alignSelf: 'flex-start' }}
                >
                    {destination.category}
                </Chip>

                {/* Name - Fixed height with truncation */}
                <Typography
                    level="title-lg"
                    sx={{
                        fontWeight: 600,
                        mb: 0.5,
                        minHeight: '3.2em',
                        lineHeight: 1.3,
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                    }}
                >
                    {destination.name}
                </Typography>

                {/* Location */}
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 1.5 }}>
                    <LocationOnIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                    <Typography
                        level="body-sm"
                        sx={{
                            color: 'text.secondary',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                        }}
                    >
                        {destination.location}
                    </Typography>
                </Box>

                {/* Rating & Price - Push to bottom */}
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 'auto' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <StarIcon sx={{ fontSize: 18, color: '#FFB800' }} />
                        <Typography level="body-sm" sx={{ fontWeight: 600 }}>
                            {destination.rating.toFixed(1)}
                        </Typography>
                    </Box>
                    <Typography
                        level="body-sm"
                        sx={{
                            fontWeight: 600,
                            color: 'primary.600',
                        }}
                    >
                        {priceLabel}
                    </Typography>
                </Box>
            </Box>
        </Card>
    );
};

export default DestinationCard;
