import os
import requests
import ctypes

# Configuration
URL = "https://huggingface.co/hukenovs/slovo/resolve/main/slovo_gestures.task"
STORAGE_DIR = r"D:\slovo"
FILE_NAME = "slovo_gestures.task"
STORAGE_PATH = os.path.join(STORAGE_DIR, FILE_NAME)
PROJECT_ASSETS_DIR = os.path.join(os.getcwd(), "app", "src", "main", "assets")
LINK_PATH = os.path.join(PROJECT_ASSETS_DIR, FILE_NAME)

def is_admin():
    try:
        return ctypes.windll.shell32.IsUserAnAdmin()
    except:
        return False

def download_file():
    # Create storage directory on D:
    if not os.path.exists(STORAGE_DIR):
        try:
            os.makedirs(STORAGE_DIR)
            print(f"Created directory: {STORAGE_DIR}")
        except Exception as e:
            print(f"Error creating directory {STORAGE_DIR}: {e}")
            return

    # Download file
    if os.path.exists(STORAGE_PATH):
        print(f"File already exists at {STORAGE_PATH}")
    else:
        print(f"Downloading {URL} to {STORAGE_PATH}...")
        try:
            response = requests.get(URL, stream=True)
            response.raise_for_status()
            
            with open(STORAGE_PATH, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            print("Download complete.")
        except Exception as e:
            print(f"Error downloading file: {e}")
            return

    # Create Symlink
    if not os.path.exists(PROJECT_ASSETS_DIR):
        os.makedirs(PROJECT_ASSETS_DIR)

    if os.path.exists(LINK_PATH):
        print(f"Link or file already exists at {LINK_PATH}")
        # Check if it's a symlink pointing to the right place? 
        # For simplicity, we assume if it exists, it's fine, or user can delete it.
    else:
        print(f"Creating symlink from {STORAGE_PATH} to {LINK_PATH}...")
        try:
            if is_admin():
                os.symlink(STORAGE_PATH, LINK_PATH)
                print("Symlink created successfully.")
            else:
                print("WARNING: Admin privileges required to create symlink.")
                print(f"Please run this script as Administrator, or manually create the link:")
                print(f'mklink "{LINK_PATH}" "{STORAGE_PATH}"')
        except Exception as e:
            print(f"Error creating symlink: {e}")

if __name__ == "__main__":
    download_file()
