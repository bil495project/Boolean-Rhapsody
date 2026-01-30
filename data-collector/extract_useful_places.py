import pandas as pd
import ast
import os
import csv

# Create output folder
output_folder = 'extracted_places'
os.makedirs(output_folder, exist_ok=True)

# Read the CSV file manually to handle variable column counts
# The CSV has rows with 8, 10, or 11 columns - we only need the first 8
rows = []
with open('places_ankara.csv', 'r', encoding='utf-8') as f:
    reader = csv.reader(f)
    header = next(reader)[:8]  # Only take first 8 columns
    for row in reader:
        rows.append(row[:8])  # Only take first 8 columns from each row

df = pd.DataFrame(rows, columns=header)

# Convert numeric columns
df['lat'] = pd.to_numeric(df['lat'], errors='coerce')
df['lng'] = pd.to_numeric(df['lng'], errors='coerce')
df['rating'] = pd.to_numeric(df['rating'], errors='coerce')
df['user_rating_count'] = pd.to_numeric(df['user_rating_count'], errors='coerce')

print(f"Total rows loaded: {len(df)}")

# Filter out places with less than 100 ratings
df = df[df['user_rating_count'] >= 100]
print(f"After filtering (>=100 ratings): {len(df)}")

# Define the type mappings for each category (based on actual types found in the data)
categories = {
    'restaurants': ['restaurant', 'turkish_restaurant', 'breakfast_restaurant', 'fine_dining_restaurant',
                    'fast_food_restaurant', 'hamburger_restaurant', 'american_restaurant', 
                    'middle_eastern_restaurant', 'sandwich_shop', 'bagel_shop', 'dessert_restaurant',
                    'brunch_restaurant', 'steak_house', 'bar_and_grill', 'cafeteria', 'food_court',
                    'meal_takeaway', 'meal_delivery', 'food_delivery'],
    
    'hotels': ['hotel'],
    
    'parks': ['park', 'national_park', 'playground', 'picnic_ground', 'garden', 'botanical_garden',
              'hiking_area', 'campground', 'athletic_field', 'golf_course'],
    
    'cafes_desserts': ['cafe', 'coffee_shop', 'bakery', 'tea_house', 'chocolate_factory'],
    
    'landmarks': ['tourist_attraction', 'amusement_park', 'water_park', 'amusement_center',
                  'stadium', 'concert_hall', 'auditorium', 'sports_complex', 'ski_resort',
                  'tour_agency', 'travel_agency'],
    
    'historic_places': ['historical_place', 'historical_landmark', 'museum', 'art_gallery',
                        'cultural_center', 'library', 'city_hall',
                        'courthouse'],
    
    'bars_nightclubs': ['bar', 'bar_and_grill']
}

# Types to exclude entirely
excluded_types = ['gas_station']

def parse_types(types_str):
    """Parse the types string into a list"""
    if pd.isna(types_str):
        return []
    try:
        return ast.literal_eval(types_str)
    except:
        return []

def check_category(types_list, category_types):
    """Check if any type in the list matches the category"""
    # Exclude gas stations
    if any(t in excluded_types for t in types_list):
        return False
    return any(t in category_types for t in types_list)

def is_historic_mosque(types_list):
    """Check if a mosque is historic (has historical_place or historical_landmark)"""
    is_mosque = 'mosque' in types_list or 'place_of_worship' in types_list
    is_historic = 'historical_place' in types_list or 'historical_landmark' in types_list
    return is_mosque and is_historic

# Parse the types column
df['types_list'] = df['types'].apply(parse_types)

# Extract and save each category
for category_name, category_types in categories.items():
    # Filter rows where types match the category
    mask = df['types_list'].apply(lambda x: check_category(x, category_types))
    category_df = df[mask].drop(columns=['types_list'])
    
    # For historic_places, also include historic mosques
    if category_name == 'historic_places':
        historic_mosque_mask = df['types_list'].apply(is_historic_mosque)
        historic_mosques_df = df[historic_mosque_mask].drop(columns=['types_list'])
        category_df = pd.concat([category_df, historic_mosques_df]).drop_duplicates(subset=['id'])
    
    # Save to CSV in the output folder
    output_file = os.path.join(output_folder, f'{category_name}.csv')
    category_df.to_csv(output_file, index=False)
    print(f"Saved {len(category_df)} {category_name} to {output_file}")

print("\nExtraction complete!")
