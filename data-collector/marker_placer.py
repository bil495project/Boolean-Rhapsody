import pandas as pd
import folium
from folium.plugins import MarkerCluster
import re
import csv
from pathlib import Path
import argparse
from pandas.errors import ParserError

# Default to root CSV
DEFAULT_CSV = Path("places_ankara.csv")
DEFAULT_OUT = Path("ankara_map.html")

def load_places(csv_path: Path) -> pd.DataFrame:
    parse_kwargs = dict(sep=",", quotechar='"', doublequote=True, encoding="utf-8-sig", low_memory=False)
    try:
        df = pd.read_csv(csv_path, **parse_kwargs)
    except ParserError:
        # Fallback to Python engine and skip bad lines (remove unsupported low_memory)
        pk_fallback = {k: v for k, v in parse_kwargs.items() if k != "low_memory"}
        df = pd.read_csv(csv_path, engine="python", on_bad_lines="skip", **pk_fallback)

    # If some rows were skipped due to malformed CSV, try to salvage lat/lng from raw lines
    try:
        total_lines = sum(1 for _ in open(csv_path, 'r', encoding='utf-8-sig')) - 1  # minus header
    except OSError:
        total_lines = None

    def _salvage_latlng(path: Path) -> pd.DataFrame:
        salvaged = []
        lat_range = (34.0, 42.5)  # Turkey lat bounds (rough)
        lng_range = (25.0, 45.0)  # Turkey lng bounds (rough)
        float_pair = re.compile(r"(-?\d{1,2}\.\d+),\s*(-?\d{1,3}\.\d+)")
        with open(path, 'r', encoding='utf-8-sig', newline='') as f:
            reader = enumerate(f)
            header = next(reader, None)  # skip header
            for i, line in reader:
                # Find candidate float pairs
                for m in float_pair.finditer(line):
                    lat = float(m.group(1))
                    lng = float(m.group(2))
                    if lat_range[0] <= lat <= lat_range[1] and lng_range[0] <= lng <= lng_range[1]:
                        # Try to get a name via csv.reader for nicer tooltip; fall back to line number
                        try:
                            row = next(csv.reader([line], delimiter=',', quotechar='"', doublequote=True))
                            name = row[1] if len(row) > 1 else f"row {i}"
                            addr = row[2] if len(row) > 2 else ""
                        except Exception:
                            name, addr = f"row {i}", ""
                        salvaged.append({"name": name, "formatted_address": addr, "lat": lat, "lng": lng})
                        break  # take first plausible pair on the line
        return pd.DataFrame(salvaged)

    orig_len = len(df)
    if total_lines is not None and orig_len < total_lines:
        salvage_df = _salvage_latlng(csv_path)
        if not salvage_df.empty:
            # Merge by lat/lng to avoid duplicates
            combined = pd.concat([df, salvage_df], ignore_index=True)
            combined["lat"] = pd.to_numeric(combined.get("lat"), errors="coerce")
            combined["lng"] = pd.to_numeric(combined.get("lng"), errors="coerce")
            # Drop exact duplicate coordinates
            combined = combined.drop_duplicates(subset=["lat", "lng"])
            df = combined

    # Ensure numeric lat/lng and drop invalid rows
    df["lat"] = pd.to_numeric(df.get("lat"), errors="coerce")
    df["lng"] = pd.to_numeric(df.get("lng"), errors="coerce")
    before = len(df)
    df = df.dropna(subset=["lat", "lng"])
    dropped = before - len(df)
    if dropped:
        print(f"Dropped {dropped} rows without valid lat/lng.")
    return df

def make_map(df: pd.DataFrame, zoom: int = 10, use_clustering: bool = True) -> folium.Map:
    center_lat = float(df["lat"].mean())
    center_lng = float(df["lng"].mean())
    fmap = folium.Map(location=[center_lat, center_lng], zoom_start=zoom, tiles="OpenStreetMap", prefer_canvas=True)

    # Create marker cluster with optimized settings for many markers
    if use_clustering:
        marker_group = MarkerCluster(
            options={
                'maxClusterRadius': 50,  # Smaller cluster radius
                'disableClusteringAtZoom': 15,  # Show individual markers when zoomed in
                'spiderfyOnMaxZoom': True,
                'showCoverageOnHover': False,
                'zoomToBoundsOnClick': True,
                'chunkedLoading': True,  # Load markers in chunks
                'chunkInterval': 200,  # Faster chunking
                'chunkDelay': 50
            }
        )
    else:
        marker_group = fmap

    # Use Canvas-based circle markers for performance with thousands of points
    for idx, row in df.iterrows():
        name = row.get("name", "")
        addr = row.get("formatted_address", "")
        rating = row.get("rating", "")
        user_count = row.get("user_rating_count", "")
        
        # Simplified popup for better performance with many markers
        popup_html = f"<b>{name}</b>"
        
        folium.CircleMarker(
            location=[row["lat"], row["lng"]],
            radius=2,  # Smaller radius
            color="#3388ff",
            weight=0,
            fill=True,
            fill_color="#3388ff",
            fill_opacity=0.6,
            popup=folium.Popup(popup_html, max_width=200) if len(df) < 2000 else None,  # No popup for huge datasets
        ).add_to(marker_group)

    # Add cluster to map if used
    if use_clustering:
        marker_group.add_to(fmap)

    # Auto-fit to data bounds to avoid empty view
    try:
        lat_min, lat_max = float(df["lat"].min()), float(df["lat"].max())
        lng_min, lng_max = float(df["lng"].min()), float(df["lng"].max())
        fmap.fit_bounds([[lat_min, lng_min], [lat_max, lng_max]])
    except Exception:
        pass

    return fmap

def parse_args():
    p = argparse.ArgumentParser(description="Render markers from a CSV onto a Folium map")
    p.add_argument("--csv", type=Path, default=DEFAULT_CSV, help="Input CSV path (default: places_ankara.csv)")
    p.add_argument("--out", type=Path, default=DEFAULT_OUT, help="Output HTML map path (default: ankara_map.html)")
    p.add_argument("--zoom", type=int, default=10, help="Initial zoom level (default: 10)")
    p.add_argument("--limit", type=int, default=None, help="Limit number of markers (for testing)")
    p.add_argument("--no-cluster", action="store_true", help="Disable marker clustering (not recommended for 1000+ markers)")
    return p.parse_args()

def main():
    args = parse_args()
    if not args.csv.exists():
        args.csv = Path("data-collector/places_ankara.csv")
    df = load_places(args.csv)
    
    # Limit markers if requested
    if args.limit is not None and args.limit > 0:
        df = df.head(args.limit)
        print(f"Limited to first {len(df)} markers")
    
    print(f"Loaded {len(df)} rows from {args.csv}")
    use_clustering = args.no_cluster
    if use_clustering:
        print("Using marker clustering (zoom in to see individual markers)")
    fmap = make_map(df, zoom=args.zoom, use_clustering=use_clustering)
    fmap.save(str(args.out))
    print(f"Map written to {args.out.resolve()} with {len(df)} markers")

if __name__ == "__main__":
    main()