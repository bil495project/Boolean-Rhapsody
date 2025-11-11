import json
import logging
import os
import torch
from transformers import pipeline, set_seed
from typing import Dict, List, Union

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

class PlaceAttributeScorer:
    """
    Uses Hugging Face zero-shot classification to assign attribute labels and scores
    to place descriptions, running on a GPU if available.
    """

    def __init__(self, model_name: str = "facebook/bart-large-mnli", seed: int = 42):
        """
        Initializes the scorer, detects GPU, and loads the model onto the correct device.
        """
        # --- GPU/CPU Device Setup ---
        # Check if a CUDA-enabled GPU is available, otherwise default to CPU
        if torch.cuda.is_available():
            device = "cuda:0"
            logging.info("✅ GPU detected. Loading model onto CUDA device.")
        else:
            device = "cpu"
            logging.info("⚠️ No GPU detected. Loading model onto CPU. This will be slower.")

        set_seed(seed)
        
        # Load the pipeline and assign it to the determined device (GPU or CPU)
        try:
            self.classifier = pipeline(
                "zero-shot-classification",
                model=model_name,
                device=device
            )
        except Exception as e:
            logging.error(f"Failed to load the model. Ensure you have 'torch' and 'transformers' installed. Error: {e}")
            raise

        # --- Attribute Definitions ---
        self.attributes = {
            "peaceful": {
                "labels": [
                    "extremely peaceful and quiet", "very peaceful", "moderately peaceful",
                    "somewhat noisy", "very noisy and bustling"
                ],
                "scores": [5, 4, 3, 2, 1]
            },
            "family_friendly": {
                "labels": [
                    "perfect for families with children", "very family-friendly", "somewhat family-friendly",
                    "slightly adult-oriented", "not suitable for families"
                ],
                "scores": [5, 4, 3, 2, 1]
            },
            "budget_friendliness": {
                "labels": [
                    "extremely affordable", "affordable", "moderately priced",
                    "expensive", "very expensive luxury"
                ],
                "scores": [5, 4, 3, 2, 1]
            },
            "nightlife_vibrancy": {
                "labels": [
                    "vibrant and active nightlife", "good nightlife options", "moderate nightlife presence",
                    "quiet at night with limited options", "no nightlife activity"
                ],
                "scores": [5, 4, 3, 2, 1]
            },
            "historical_significance": {
                "labels": [
                    "exceptionally rich in history and heritage", "historically significant",
                    "some historical sites present", "few historical elements", "not historical"
                ],
                "scores": [5, 4, 3, 2, 1]
            }
        }

    def classify_description(self, description: str) -> Dict[str, Dict[str, Union[str, int]]]:
        """
        Classifies the given description across all defined attributes.
        """
        if not description or not isinstance(description, str) or len(description.strip()) < 10:
            logging.warning(f"Skipping invalid or too short description: '{description}'")
            return {attr: {"label": "N/A", "score": 0} for attr in self.attributes}

        results = {}
        for attr, data in self.attributes.items():
            try:
                # The pipeline will automatically handle batching on the GPU
                output = self.classifier(
                    sequences=description,
                    candidate_labels=data["labels"],
                    multi_label=False
                )
                best_label = output["labels"][0]
                # Map the winning label back to its corresponding score
                score_index = data["labels"].index(best_label)
                score = data["scores"][score_index]
                results[attr] = {"label": best_label, "score": score}
            except Exception as e:
                logging.error(f"Error classifying '{attr}' for description: '{description[:50]}...'. Error: {e}")
                results[attr] = {"label": "Classification Error", "score": 0}
        return results


def process_data(input_filepath: str, output_filepath: str):
    """
    Loads data, runs classification, and saves the results.
    """
    if not os.path.exists(input_filepath):
        logging.error(f"Input file not found: {input_filepath}")
        return

    with open(input_filepath, "r", encoding="utf-8") as f:
        data = json.load(f)

    place_desc_dict = data.get("place_descriptions")
    
    if not place_desc_dict:
        logging.error("No 'place_descriptions' key found or it is empty in the input file.")
        return

    # Initialize the scorer (which will auto-detect the GPU)
    scorer = PlaceAttributeScorer()

    logging.info(f"Processing {len(place_desc_dict)} places.")
    output_data = {}

    for place, description in place_desc_dict.items():
        logging.info(f"----> Classifying place: {place}")
        scores = scorer.classify_description(description)
        output_data[place] = scores

    # Write the final output to a JSON file
    with open(output_filepath, "w", encoding="utf-8") as outfile:
        json.dump(output_data, outfile, indent=4, ensure_ascii=False)

    logging.info(f"✅ Classification complete. Results saved to {output_filepath}")


if __name__ == "__main__":
    # Ensure you have run the previous script to generate this input file
    input_file = "ankara_blog_data.json"
    output_file = "labels_extracted_gpu.json"

    process_data(input_file, output_file)
