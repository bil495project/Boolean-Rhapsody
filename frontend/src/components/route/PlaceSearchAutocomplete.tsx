import {
    Autocomplete,
    AutocompleteOption,
    FormControl,
    FormLabel,
    ListItemContent,
    Typography,
} from '@mui/joy';
import SearchIcon from '@mui/icons-material/Search';
import type { MapDestination } from '../../data/destinations';

interface PlaceSearchAutocompleteProps {
    label?: string;
    placeholder?: string;
    value: MapDestination | null;
    options: MapDestination[];
    loading?: boolean;
    onInputChange: (query: string) => void;
    onChange: (value: MapDestination | null) => void;
}

const PlaceSearchAutocomplete = ({
    label,
    placeholder = 'Type to search places...',
    value,
    options,
    loading = false,
    onInputChange,
    onChange,
}: PlaceSearchAutocompleteProps) => (
    <FormControl size="sm" sx={{ flex: 1, minWidth: 250 }}>
        {label && <FormLabel>{label}</FormLabel>}
        <Autocomplete
            placeholder={placeholder}
            options={options}
            getOptionLabel={(option) => option.name}
            isOptionEqualToValue={(option, selected) => option.id === selected.id}
            value={value}
            loading={loading}
            onInputChange={(_event, query) => onInputChange(query)}
            onChange={(_event, selected) => onChange(selected)}
            startDecorator={<SearchIcon sx={{ fontSize: 18 }} />}
            renderOption={(props, option) => (
                <AutocompleteOption {...props} key={option.id}>
                    <ListItemContent>
                        <Typography level="body-sm" sx={{ fontWeight: 600 }}>
                            {option.name}
                        </Typography>
                        <Typography level="body-xs" sx={{ color: 'text.secondary' }}>
                            {option.location}
                        </Typography>
                    </ListItemContent>
                </AutocompleteOption>
            )}
        />
    </FormControl>
);

export default PlaceSearchAutocomplete;
