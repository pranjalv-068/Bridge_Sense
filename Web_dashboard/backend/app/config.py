import os
from pathlib import Path

BACKEND_DIR = Path(__file__).resolve().parent.parent
PROJECT_DIR = BACKEND_DIR.parent

DATA_DIR = BACKEND_DIR / "data"
DATA_DIR.mkdir(exist_ok=True)
DB_PATH = DATA_DIR / "bridgesense.db"

HEALTHY_MAX = 0.312
WARNING_MAX = 0.541
MAJOR_MAX = 0.880

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
