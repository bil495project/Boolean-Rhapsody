import { useCallback, useRef, useState } from 'react';
import { Box } from '@mui/joy';
import { useAppDispatch } from '../../store/hooks';
import { setChatPanelWidth } from '../../store/chatSlice';

interface ResizableDividerProps {
    orientation?: 'vertical' | 'horizontal';
}

const ResizableDivider = ({ orientation = 'vertical' }: ResizableDividerProps) => {
    const dispatch = useAppDispatch();
    const [isDragging, setIsDragging] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    const handleMouseDown = useCallback((e: React.MouseEvent) => {
        e.preventDefault();
        setIsDragging(true);

        const handleMouseMove = (e: MouseEvent) => {
            const container = containerRef.current?.parentElement;
            if (!container) return;

            const rect = container.getBoundingClientRect();
            const percentage = orientation === 'vertical'
                ? ((e.clientX - rect.left) / rect.width) * 100
                : ((e.clientY - rect.top) / rect.height) * 100;

            dispatch(setChatPanelWidth(percentage));
        };

        const handleMouseUp = () => {
            setIsDragging(false);
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);
    }, [dispatch, orientation]);

    const handleTouchStart = useCallback((_e: React.TouchEvent) => {
        setIsDragging(true);

        const handleTouchMove = (e: TouchEvent) => {
            const container = containerRef.current?.parentElement;
            if (!container || !e.touches[0]) return;

            const rect = container.getBoundingClientRect();
            const percentage = orientation === 'vertical'
                ? ((e.touches[0].clientX - rect.left) / rect.width) * 100
                : ((e.touches[0].clientY - rect.top) / rect.height) * 100;

            dispatch(setChatPanelWidth(percentage));
        };

        const handleTouchEnd = () => {
            setIsDragging(false);
            document.removeEventListener('touchmove', handleTouchMove);
            document.removeEventListener('touchend', handleTouchEnd);
        };

        document.addEventListener('touchmove', handleTouchMove);
        document.addEventListener('touchend', handleTouchEnd);
    }, [dispatch, orientation]);

    return (
        <Box
            ref={containerRef}
            onMouseDown={handleMouseDown}
            onTouchStart={handleTouchStart}
            sx={{
                width: orientation === 'vertical' ? 6 : '100%',
                height: orientation === 'vertical' ? '100%' : 6,
                cursor: orientation === 'vertical' ? 'col-resize' : 'row-resize',
                bgcolor: isDragging ? 'primary.500' : 'transparent',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
                transition: 'background-color 0.15s',
                '&:hover': {
                    bgcolor: 'primary.300',
                },
                '&::before': {
                    content: '""',
                    width: orientation === 'vertical' ? 2 : '40px',
                    height: orientation === 'vertical' ? '40px' : 2,
                    bgcolor: isDragging ? 'primary.200' : 'neutral.400',
                    borderRadius: 'lg',
                    transition: 'background-color 0.15s',
                },
            }}
        />
    );
};

export default ResizableDivider;
