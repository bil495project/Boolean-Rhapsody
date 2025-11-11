import requests
from bs4 import BeautifulSoup
import spacy
from textblob import TextBlob
from collections import defaultdict
import re
import json
import os


class BlogDataExtractor:
    def __init__(self, url):
        self.url = url
        self.raw_text = ""
        self.entities = []
        self.sentiment = {"polarity": None, "subjectivity": None}
        self.nlp = spacy.load("en_core_web_sm")
    
    def fetch_blog_text(self):
        response = requests.get(self.url)
        soup = BeautifulSoup(response.text, 'html.parser')
        content_div = soup.find('div', class_='entry-content')
        if not content_div:
            print("⚠️ Could not find blog content.")
            return
        paragraphs = content_div.find_all(['p', 'h2', 'h3', 'li'])
        self.raw_text = ' '.join(p.get_text() for p in paragraphs)

    def extract_named_entities(self):
        doc = self.nlp(self.raw_text)
        self.entities = list(set(
            ent.text.strip() for ent in doc.ents 
            if ent.label_ in ("GPE", "LOC", "ORG")
        ))
    
    def analyze_sentiment(self):
        blob = TextBlob(self.raw_text)
        self.sentiment["polarity"] = blob.sentiment.polarity
        self.sentiment["subjectivity"] = blob.sentiment.subjectivity
    
    def process(self):
        self.fetch_blog_text()
        self.extract_named_entities()
        self.analyze_sentiment()
    
    
    def extract_place_descriptions(self):
        from nltk.tokenize import sent_tokenize
        import nltk
        nltk.download('punkt')
        
        place_sentences = defaultdict(list)
        sentences = sent_tokenize(self.raw_text)

        for place in self.entities:
            pattern = re.compile(re.escape(place), re.IGNORECASE)
            for sent in sentences:
                if pattern.search(sent):
                    place_sentences[place].append(sent.strip())

        self.place_descriptions = {
            place: ' '.join(sents[:2]) for place, sents in place_sentences.items()
        }

    def process(self):
        self.fetch_blog_text()
        if self.raw_text:
            self.extract_named_entities()
            self.analyze_sentiment()
            self.extract_place_descriptions()

    def get_summary(self):
        return {
            "url": self.url,
            "entities": self.entities,
            "sentiment": self.sentiment,
            "place_descriptions": getattr(self, 'place_descriptions', {}),
            "text_preview": self.raw_text[:500] + "..."
        }

# Example usage
if __name__ == "__main__":
    web_address = "https://fethiyenow.com/blog/things-to-do-in-ankara/"
    print("Working")
    print("Working2")
    extractor = BlogDataExtractor(web_address)
    
    extractor.process()
    summary = extractor.get_summary()
    
    print("Working3")
    print(summary)
     # Save to JSON file
    with open("ankara_blog_data.json", "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    print("📂 File path:", os.path.abspath("ankara_blog_data.json"))
    print("✅ Blog data saved to ankara_blog_data.json")

