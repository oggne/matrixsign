import os
import cv2
import mediapipe as mp
import csv
import argparse
from tqdm import tqdm

# Настройка аргументов командной строки
parser = argparse.ArgumentParser(description='Process Slovo dataset for MatrixSign training.')
parser.add_argument('--dataset_path', type=str, required=True, help='Path to the root of the Slovo dataset')
parser.add_argument('--output_csv', type=str, default='rsl_data.csv', help='Path to the output CSV file')
parser.add_argument('--append', action='store_true', help='Append to existing CSV instead of overwriting')
args = parser.parse_args()

# Инициализация MediaPipe
mp_hands = mp.solutions.hands
hands = mp_hands.Hands(
    static_image_mode=False,
    max_num_hands=2,
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5
)

def process_video(video_path, label, writer):
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"Error opening video: {video_path}")
        return

    frame_count = 0
    while cap.isOpened():
        success, image = cap.read()
        if not success:
            break

        # Конвертация BGR -> RGB
        image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        
        # Обработка кадра
        results = hands.process(image_rgb)

        if results.multi_hand_landmarks:
            for hand_landmarks in results.multi_hand_landmarks:
                # Формируем строку данных
                row = [label]
                for landmark in hand_landmarks.landmark:
                    row.extend([landmark.x, landmark.y, landmark.z])
                
                # Записываем в CSV
                writer.writerow(row)
                frame_count += 1

    cap.release()
    return frame_count

def main():
    dataset_root = args.dataset_path
    output_file = args.output_csv
    
    # Проверка существования датасета
    if not os.path.exists(dataset_root):
        print(f"Error: Dataset path '{dataset_root}' does not exist.")
        return

    # Режим открытия файла
    mode = 'a' if args.append else 'w'
    
    print(f"Scanning dataset at: {dataset_root}")
    print(f"Output file: {output_file}")

    # Сбор всех видео файлов
    video_files = []
    for root, dirs, files in os.walk(dataset_root):
        for file in files:
            if file.lower().endswith(('.mp4', '.avi', '.mov')):
                # Предполагаем структуру: dataset/LABEL/video.mp4
                # Или dataset/video_LABEL.mp4 (нужна адаптация под реальную структуру Slovo)
                # Для Slovo обычно есть аннотации, но для простоты берем имя папки как метку
                label = os.path.basename(root)
                full_path = os.path.join(root, file)
                video_files.append((full_path, label))

    if not video_files:
        print("No video files found.")
        return

    print(f"Found {len(video_files)} videos.")

    with open(output_file, mode, newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        
        # Записываем заголовок только если создаем новый файл
        if mode == 'w':
            header = ['label']
            for i in range(21):
                header.extend([f'x{i}', f'y{i}', f'z{i}'])
            writer.writerow(header)

        # Обработка видео с прогресс-баром
        total_frames = 0
        for video_path, label in tqdm(video_files, desc="Processing videos"):
            frames = process_video(video_path, label, writer)
            if frames:
                total_frames += frames

    print(f"\nProcessing complete!")
    print(f"Total samples extracted: {total_frames}")
    print(f"Data saved to: {output_file}")
    print("\nNext step: Run 'python train_rsl_model.py' to train your model.")

if __name__ == '__main__':
    main()
