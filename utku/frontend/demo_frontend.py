from flask import Flask, redirect

app = Flask(__name__)

@app.route('/')
def home():
    # For simplicity nothing too fancy
    return redirect("http://localhost:5000")

if __name__ == "__main__":
    print("[FRONTEND] Gateway starting on port 3700...")
    app.run(port=3700)