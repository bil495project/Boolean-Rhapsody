import { useCallback, useRef, useState } from 'react';
import { Box } from '@mui/joy';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { setChatPanelWidth } from '../../store/chatSlice';

interface ResizableDividerProps {
    orientation?: 'vertical' | 'horizontal';
}

const ResizableDivider = ({ orientation = 'vertical' }: ResizableDividerProps) => {
    const dispatch = useAppDispatch();
    const chatPanelWidth = useAppSelector((state) => state.chat.chatPanelWidth);
    const [isDragging, setIsDragging] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);
    const latestPercentageRef = useRef<number>(chatPanelWidth);

    const handleMouseDown = useCallback((e: React.MouseEvent) => {
        e.preventDefault();
        setIsDragging(true);

        const divider = containerRef.current;
        if (!divider) return;

        const parentContainer = divider.parentElement;
        if (!parentContainer) return;

        // Signal to ResizeObservers that a drag is in progress
        parentContainer.setAttribute('data-resizing', 'true');

        // Find sibling panels: previous sibling = left panel, next sibling = right panel
        const leftPanel = divider.previousElementSibling as HTMLElement | null;
        const rightPanel = divider.nextElementSibling as HTMLElement | null;

        const handleMouseMove = (e: MouseEvent) => {
            if (!parentContainer) return;

            const rect = parentContainer.getBoundingClientRect();
            const percentage = orientation === 'vertical'
                ? ((e.clientX - rect.left) / rect.width) * 100
                : ((e.clientY - rect.top) / rect.height) * 100;

            // Clamp to 30-70% range (same as Redux reducer)
            const clamped = Math.max(30, Math.min(70, percentage));
            latestPercentageRef.current = clamped;

            // Directly manipulate DOM — no React re-renders
            if (leftPanel) {
                leftPanel.style.width = `${clamped}%`;
            }
            if (rightPanel) {
                rightPanel.style.width = `${100 - clamped}%`;
            }
        };

        const handleMouseUp = () => {
            setIsDragging(false);
            parentContainer.removeAttribute('data-resizing');
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);

            // Commit final value to Redux (single re-render)
            dispatch(setChatPanelWidth(latestPercentageRef.current));
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);
    }, [dispatch, orientation]);

    const handleTouchStart = useCallback((_e: React.TouchEvent) => {
        setIsDragging(true);

        const divider = containerRef.current;
        if (!divider) return;

        const parentContainer = divider.parentElement;
        if (!parentContainer) return;

        parentContainer.setAttribute('data-resizing', 'true');

        const leftPanel = divider.previousElementSibling as HTMLElement | null;
        const rightPanel = divider.nextElementSibling as HTMLElement | null;

        const handleTouchMove = (e: TouchEvent) => {
            if (!parentContainer || !e.touches[0]) return;

            const rect = parentContainer.getBoundingClientRect();
            const percentage = orientation === 'vertical'
                ? ((e.touches[0].clientX - rect.left) / rect.width) * 100
                : ((e.touches[0].clientY - rect.top) / rect.height) * 100;

            const clamped = Math.max(30, Math.min(70, percentage));
            latestPercentageRef.current = clamped;

            if (leftPanel) {
                leftPanel.style.width = `${clamped}%`;
            }
            if (rightPanel) {
                rightPanel.style.width = `${100 - clamped}%`;
            }
        };

        const handleTouchEnd = () => {
            setIsDragging(false);
            parentContainer.removeAttribute('data-resizing');
            document.removeEventListener('touchmove', handleTouchMove);
            document.removeEventListener('touchend', handleTouchEnd);

            dispatch(setChatPanelWidth(latestPercentageRef.current));
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
