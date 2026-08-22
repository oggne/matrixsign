import os
import shutil
import ctypes
import sys

# Configuration
DOWNLOADS_DIR = r"C:\Users\Admin\Downloads"
FILE_NAME = "slovo_gestures.task"
SOURCE_FILE = os.path.join(DOWNLOADS_DIR, FILE_NAME)

STORAGE_DIR = r"D:\slovo"
STORAGE_PATH = os.path.join(STORAGE_DIR, FILE_NAME)

PROJECT_ASSETS_DIR = os.path.join(os.getcwd(), "app", "src", "main", "assets")
LINK_PATH = os.path.join(PROJECT_ASSETS_DIR, FILE_NAME)

def is_admin():
    try:
        return ctypes.windll.shell32.IsUserAnAdmin()
    except:
        return False

def setup_model():
    print(f"Checking for {FILE_NAME} in Downloads...")
    if not os.path.exists(SOURCE_FILE):
        print(f"Error: File not found at {SOURCE_FILE}")
        print("Please ensure you have downloaded 'slovo_gestures.task' to your Downloads folder.")
        return

    # Create storage directory on D:
    if not os.path.exists(STORAGE_DIR):
        try:
            os.makedirs(STORAGE_DIR)
            print(f"Created directory: {STORAGE_DIR}")
        except Exception as e:
            print(f"Error creating directory {STORAGE_DIR}: {e}")
            return

    # Move file
    print(f"Moving file to {STORAGE_PATH}...")
    try:
        shutil.move(SOURCE_FILE, STORAGE_PATH)
        print("File moved successfully.")
    except Exception as e:
        print(f"Error moving file: {e}")
        # If move fails (e.g. across drives sometimes), try copy then remove
        try:
            shutil.copy2(SOURCE_FILE, STORAGE_PATH)
            os.remove(SOURCE_FILE)
            print("File copied and original removed.")
        except Exception as e2:
            print(f"Error copying file: {e2}")
            return

    # Create Symlink
    if not os.path.exists(PROJECT_ASSETS_DIR):
        os.makedirs(PROJECT_ASSETS_DIR)

    if os.path.exists(LINK_PATH):
        print(f"Link or file already exists at {LINK_PATH}")
    else:
        print(f"Creating symlink from {STORAGE_PATH} to {LINK_PATH}...")
        try:
            if is_admin():
                os.symlink(STORAGE_PATH, LINK_PATH)
                print("Symlink created successfully.")
            else:
                print("WARNING: Admin privileges required to create symlink.")
                print("Please run this script as Administrator.")
                print(f"Or manually run: mklink \"{LINK_PATH}\" \"{STORAGE_PATH}\"")
        except Exception as e:
            print(f"Error creating symlink: {e}")

if __name__ == "__main__":
    setup_model()
