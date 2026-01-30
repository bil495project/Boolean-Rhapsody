import pandas as pd
import ast
import os
import re

# Create output folder
output_folder = 'extracted_nightlife'
os.makedirs(output_folder, exist_ok=True)

# Read the CSV file with error handling for malformed lines
df = pd.read_csv('places_ankara.csv', on_bad_lines='skip')

# Filter out places with less than 5 ratings
df = df[df['user_rating_count'] >= 5]

# Types to look for
nightlife_types = ['bar', 'night_club', 'pub', 'wine_bar', 'cocktail_bar', 'lounge', 
                   'beer_garden', 'brewery', 'nightlife', 'club', 'bar_and_grill']

# Keywords to search in names (case insensitive) - using word boundaries to avoid false positives
nightlife_keywords_exact = [
    'bar', 'pub', 'gece klubü', 'gece kulübü', 'night club', 'nightclub', 
    'disco', 'disko', 'meyhane', 'tavern', 'taverna', 'cocktail bar', 
    'kokteyl bar', 'wine bar', 'şarap bar', 'birahanesi', 'birahane'
]

# Keywords that need word boundary check (to avoid matching "Aybarlar", "Baranka" etc.)
nightlife_keywords_word = ['bar', 'pub', 'club', 'lounge']

# Exclude patterns (false positives)
exclude_patterns = [
    'barınak', 'barinagi', 'barınağı', 'kopek',  # shelter / dog
    'country club', 'equestrian club', 'golf club', 'sports club',  # sports clubs
    'çiftliği', 'çiftligi', 'ciftligi',  # farms
    'inşaat', 'insaat',  # construction
    'karavan', 'düğün', 'dugun', 'otel', 'hotel',  # wedding/hotel venues
    'kebap', 'pide', 'restoran', 'restaurant', 'lokanta',  # restaurants
    'market', 'mangal', 'et market',  # markets / BBQ
    'baranka', 'cennet bahçesi'  # specific false positives
]

def parse_types(types_str):
    """Parse the types string into a list"""
    if pd.isna(types_str):
        return []
    try:
        return ast.literal_eval(types_str)
    except:
        return []

def check_types(types_list):
    """Check if any type matches nightlife types"""
    return any(t in nightlife_types for t in types_list)

def should_exclude(name):
    """Check if name should be excluded based on patterns"""
    if pd.isna(name):
        return False
    name_lower = name.lower()
    return any(excl in name_lower for excl in exclude_patterns)

def check_name(name):
    """Check if name contains nightlife keywords"""
    if pd.isna(name):
        return False
    name_lower = name.lower()
    
    # First check exclusions
    if should_exclude(name):
        return False
    
    # Check exact keyword matches
    if any(keyword in name_lower for keyword in nightlife_keywords_exact):
        return True
    
    # Check word-boundary keywords using regex
    for keyword in nightlife_keywords_word:
        # Match keyword as a whole word (not part of another word)
        pattern = r'\b' + re.escape(keyword) + r'\b'
        if re.search(pattern, name_lower):
            return True
    
    return False

# Parse the types column
df['types_list'] = df['types'].apply(parse_types)

# Find by types OR by name, but exclude false positives
type_mask = df['types_list'].apply(check_types) & ~df['name'].apply(should_exclude)
name_mask = df['name'].apply(check_name)

# Combine both masks
combined_mask = type_mask | name_mask

# Get results
nightlife_df = df[combined_mask].copy()

# Add a column to indicate how it was found
nightlife_df['found_by'] = nightlife_df.apply(
    lambda row: 'both' if check_types(row['types_list']) and check_name(row['name'])
    else ('type' if check_types(row['types_list']) else 'name'),
    axis=1
)

# Drop the types_list column
nightlife_df = nightlife_df.drop(columns=['types_list'])

# Save to CSV
output_file = os.path.join(output_folder, 'bars_nightclubs.csv')
nightlife_df.to_csv(output_file, index=False)

print(f"Found {len(nightlife_df)} bars/nightclubs total")
print(f"  - Found by type: {len(nightlife_df[nightlife_df['found_by'] == 'type'])}")
print(f"  - Found by name: {len(nightlife_df[nightlife_df['found_by'] == 'name'])}")
print(f"  - Found by both: {len(nightlife_df[nightlife_df['found_by'] == 'both'])}")
print(f"\nSaved to {output_file}")

# Print the results
print("\n--- Results ---\n")
for _, row in nightlife_df.iterrows():
    print(f"- {row['name']} | Rating: {row['rating']} ({row['user_rating_count']} reviews) | Found by: {row['found_by']}")
