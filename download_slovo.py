import os
import requests
import zipfile
from tqdm import tqdm

# Configuration
URL = "https://rndml-team-cv.obs.ru-moscow-1.hc.sbercloud.ru/datasets/slovo/slovo.zip"
DEST_DIR = "slovo_dataset"
ZIP_FILE = os.path.join(DEST_DIR, "slovo.zip")

def download_file(url, filename):
    """Downloads a file with a progress bar and resume support."""
    if not os.path.exists(DEST_DIR):
        os.makedirs(DEST_DIR)

    # Get total size
    response = requests.head(url)
    total_size_in_bytes = int(response.headers.get('content-length', 0))
    
    headers = {}
    mode = 'wb'
    downloaded_size = 0
    
    if os.path.exists(filename):
        downloaded_size = os.path.getsize(filename)
        if downloaded_size < total_size_in_bytes:
            print(f"Resuming download from {downloaded_size} bytes...")
            headers['Range'] = f'bytes={downloaded_size}-'
            mode = 'ab'
        elif downloaded_size == total_size_in_bytes:
            print("File already fully downloaded.")
            return True
        else:
            print("Local file is larger than remote file. Redownloading...")
            downloaded_size = 0

    block_size = 1024 * 1024 # 1MB
    
    try:
        response = requests.get(url, headers=headers, stream=True)
        response.raise_for_status()
        
        progress_bar = tqdm(total=total_size_in_bytes, initial=downloaded_size, unit='iB', unit_scale=True)

        with open(filename, mode) as file:
            for data in response.iter_content(block_size):
                progress_bar.update(len(data))
                file.write(data)
        progress_bar.close()
    except Exception as e:
        print(f"Error during download: {e}")
        return False
    
    # Verify size
    if os.path.getsize(filename) != total_size_in_bytes:
        print("ERROR: Download incomplete.")
        return False
        
    return True

def extract_zip(zip_path, extract_to):
    """Extracts a zip file."""
    print(f"Extracting {zip_path} to {extract_to}...")
    try:
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(extract_to)
        print("Extraction complete.")
    except zipfile.BadZipFile:
        print("Error: Bad zip file. It might be corrupted.")

def main():
    print(f"Downloading Slovo dataset from {URL}...")
    if download_file(URL, ZIP_FILE):
        print("Download finished. Extracting...")
        extract_zip(ZIP_FILE, DEST_DIR)
        print("Done! Dataset is ready in", DEST_DIR)
    else:
        print("Download failed or interrupted. Run the script again to resume.")

if __name__ == "__main__":
    main()
