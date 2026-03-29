/**
 * Normalizes a string for fuzzy/accent-insensitive search, 
 * specifically handling Turkish characters (ö, ç, ü, ğ, ı, ş).
 */
export function normalizeForSearch(str: string): string {
    if (!str) return '';
    
    return str
        .toLowerCase()
        // Handle Turkish specific dotless/dotted i issues
        .replace(/ı/g, 'i')
        .replace(/İ/g, 'i')
        // Standard Turkish character mapping
        .replace(/ö/g, 'o')
        .replace(/ü/g, 'u')
        .replace(/ç/g, 'c')
        .replace(/ğ/g, 'g')
        .replace(/ş/g, 's')
        // Strip other accents if any (NFD normalization)
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '');
}
