import os
import requests
import time

# Configuration
URL = "https://rndml-team-cv.obs.ru-moscow-1.hc.sbercloud.ru/datasets/slovo/slovo.zip"
DEST_DIR = "slovo_dataset"
ZIP_FILE = os.path.join(DEST_DIR, "slovo.zip")

def download_file(url, filename):
    print("Starting download script...")
    if not os.path.exists(DEST_DIR):
        os.makedirs(DEST_DIR)

    print(f"Checking URL: {url}")
    try:
        # Get total size
        response = requests.head(url, timeout=10)
        total_size_in_bytes = int(response.headers.get('content-length', 0))
        print(f"Total size: {total_size_in_bytes} bytes")
    except Exception as e:
        print(f"Error checking URL: {e}")
        return False
    
    headers = {}
    mode = 'wb'
    downloaded_size = 0
    
    if os.path.exists(filename):
        downloaded_size = os.path.getsize(filename)
        print(f"Found existing file: {downloaded_size} bytes")
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
        print("Sending GET request...")
        response = requests.get(url, headers=headers, stream=True, timeout=30)
        response.raise_for_status()
        print("Response received. Starting data transfer...")
        
        with open(filename, mode) as file:
            start_time = time.time()
            bytes_since_last_print = 0
            for data in response.iter_content(block_size):
                file.write(data)
                downloaded_size += len(data)
                bytes_since_last_print += len(data)
                
                if time.time() - start_time > 5:
                    print(f"Downloaded: {downloaded_size} / {total_size_in_bytes} bytes ({(downloaded_size/total_size_in_bytes)*100:.2f}%)")
                    start_time = time.time()
                    
    except Exception as e:
        print(f"Error during download: {e}")
        return False
    
    # Verify size
    if os.path.getsize(filename) != total_size_in_bytes:
        print("ERROR: Download incomplete.")
        return False
        
    return True

if __name__ == "__main__":
    if download_file(URL, ZIP_FILE):
        print("Download finished successfully.")
    else:
        print("Download failed.")
