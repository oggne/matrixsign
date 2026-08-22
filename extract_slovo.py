import zipfile
import os

ZIP_FILE = "slovo_dataset/slovo.zip"
DEST_DIR = "slovo_dataset"

def extract_zip(zip_path, extract_to):
    """Extracts a zip file."""
    print(f"Extracting {zip_path} to {extract_to}...")
    try:
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(extract_to)
        print("Extraction complete.")
    except zipfile.BadZipFile:
        print("Error: Bad zip file. It might be corrupted.")
    except Exception as e:
        print(f"Error during extraction: {e}")

if __name__ == "__main__":
    if os.path.exists(ZIP_FILE):
        extract_zip(ZIP_FILE, DEST_DIR)
    else:
        print(f"File not found: {ZIP_FILE}")
