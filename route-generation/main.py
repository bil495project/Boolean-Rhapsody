"""
Giriş noktası — extracted_places klasöründeki tüm CSV dosyalarını yükleyip
örnek rota üretir.
"""

from __future__ import annotations

import os

from poi_controller import POIController
from trip_request import TripRequest

# -------------------------
# Veri yolu
# -------------------------
DATA_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "data-collector",
    "extracted_places",
)


def main() -> None:
    # 1) Tüm CSV'leri yükle
    repo = POIController()
    repo.load_directory(DATA_DIR)
    print(f"Toplam yüklenen yer sayısı: {len(repo.getAllPlaces())}")

    # 2) TripRequest oluştur
    req = TripRequest(_repo=repo)

    # 3) userVector örneği
    user_vector = {
        "requestId": "req-ankara-1",
        "maxStops": "5",
        "maxBudgetMin": "240",
        "mandatoryTypes": "restaurant,park",
        "minRating": "4.0",
        "centerLat": "39.9208",
        "centerLng": "32.8541",
        "radiusKm": "15",
        "mode": "driving",
        "weight_restaurant": "1.2",
        "weight_park": "1.0",
        "weight_cafe": "0.8",
        "weight_hotel": "0.4",
    }

    routes = req.generateRoutes(user_vector, k=3)

    for r in routes:
        print(f"\n--- {r.routeId}  feasible={r.feasible} ---")
        print(
            f"totalDurationSec={r.totalDurationSec}  "
            f"totalDistanceM={int(r.totalDistanceM)}"
        )
        for rp in r.points:
            print(
                f"  {rp.index:02d}  {rp.poi.name}  "
                f"rating={rp.poi.ratingScore}  ({rp.plannedVisitMin}m)"
            )

    # 4) Reroll örneği
    if routes:
        route0 = routes[0]
        req.rerollRoutePoint(
            route0, index=2, indexParams={"type": "restaurant", "minRating": "4.0"}
        )
        print("\nAfter reroll index=2:")
        for rp in route0.points:
            print(
                f"  {rp.index:02d}  {rp.poi.name}  rating={rp.poi.ratingScore}"
            )


if __name__ == "__main__":
    main()
