import os
import zipfile
import csv
import cv2
import mediapipe as mp
import tempfile
import shutil
from tqdm import tqdm

# Configuration
ZIP_FILE = "slovo_dataset/slovo.zip"
OUTPUT_CSV = "rsl_data.csv"
ANNOTATIONS_FILE = "annotations.csv"

# Initialize MediaPipe
mp_hands = mp.solutions.hands
hands = mp_hands.Hands(
    static_image_mode=False,
    max_num_hands=2,
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5
)

def load_annotations(zip_ref):
    """Parses annotations.csv."""
    print("Loading annotations...")
    
    # Check for external annotations file
    external_annotations = os.path.join(os.path.dirname(ZIP_FILE), ANNOTATIONS_FILE)
    if os.path.exists(external_annotations):
        print(f"Found external annotations file: {external_annotations}")
        try:
            with open(external_annotations, 'r', encoding='utf-8') as f:
                content = f.read().splitlines()
        except Exception as e:
            print(f"Error reading external annotations: {e}")
            content = None
    else:
        content = None

    # Fallback to zip
    if content is None:
        print(f"Extracting {ANNOTATIONS_FILE} from zip...")
        try:
            with zip_ref.open(ANNOTATIONS_FILE) as f:
                content = f.read().decode('utf-8').splitlines()
        except KeyError:
             print(f"Error: {ANNOTATIONS_FILE} not found in zip.")
             return {}
        except Exception as e:
            print(f"Error reading from zip: {e}")
            return {}

    try:    
        reader = csv.DictReader(content, delimiter='\t')
        annotations = {}
        for row in reader:
            # Map attachment_id (filename without ext) to text (label)
            # The zip structure is train/UUID.mp4
            # annotations.csv has attachment_id which matches the UUID
            annotations[row['attachment_id']] = row['text']
            
        print(f"Loaded {len(annotations)} annotations.")
        return annotations
    except Exception as e:
        print(f"Error parsing annotations: {e}")
        return {}

def process_video(video_path, label, writer):
    """Processes a single video file to extract landmarks."""
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        return 0

    frame_count = 0
    while cap.isOpened():
        success, image = cap.read()
        if not success:
            break

        # Convert BGR -> RGB
        image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        
        # Process frame
        results = hands.process(image_rgb)

        if results.multi_hand_landmarks:
            for hand_landmarks in results.multi_hand_landmarks:
                # Create data row
                row = [label]
                for landmark in hand_landmarks.landmark:
                    row.extend([landmark.x, landmark.y, landmark.z])
                
                # Write to CSV
                writer.writerow(row)
                frame_count += 1

    cap.release()
    return frame_count

def main():
    if not os.path.exists(ZIP_FILE):
        print(f"Error: {ZIP_FILE} not found.")
        return

    print(f"Processing dataset from {ZIP_FILE}...")
    
    try:
        with zipfile.ZipFile(ZIP_FILE, 'r') as zip_ref:
            # Load annotations
            annotations = load_annotations(zip_ref)
            if not annotations:
                print("Failed to load annotations. Exiting.")
                return

            # Get list of video files
            all_files = zip_ref.namelist()
            video_files = [f for f in all_files if f.endswith('.mp4') and f.startswith('train/')]
            
            print(f"Found {len(video_files)} video files in zip.")

            # Prepare CSV output
            with open(OUTPUT_CSV, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                
                # Write header
                header = ['label']
                for i in range(21):
                    header.extend([f'x{i}', f'y{i}', f'z{i}'])
                writer.writerow(header)

                # Process videos
                total_samples = 0
                processed_count = 0
                
                # Create a temporary directory for extraction
                with tempfile.TemporaryDirectory() as temp_dir:
                    print(f"Using temporary directory: {temp_dir}")
                    
                    for video_file in tqdm(video_files, desc="Processing videos"):
                        # Extract UUID from filename (train/UUID.mp4)
                        filename = os.path.basename(video_file)
                        uuid = os.path.splitext(filename)[0]
                        
                        if uuid not in annotations:
                            continue
                            
                        label = annotations[uuid]
                        
                        # Extract single video to temp dir
                        extracted_path = zip_ref.extract(video_file, path=temp_dir)
                        
                        # Process video
                        samples = process_video(extracted_path, label, writer)
                        total_samples += samples
                        processed_count += 1
                        
                        # Delete extracted file to save space
                        os.remove(extracted_path)
                        
                        # Optional: Limit for testing
                        # if processed_count >= 10: break

    except Exception as e:
        print(f"An error occurred: {e}")
        import traceback
        traceback.print_exc()

    print(f"\nProcessing complete!")
    print(f"Processed {processed_count} videos.")
    print(f"Total samples extracted: {total_samples}")
    print(f"Data saved to: {OUTPUT_CSV}")

if __name__ == "__main__":
    main()
