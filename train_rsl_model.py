import csv
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
import os
import shutil

# Конфигурация
DATASET_PATH = 'rsl_data.csv'
MODEL_SAVE_PATH = 'rsl_classifier.tflite'
LABELS_SAVE_PATH = 'rsl_labels.txt'
NUM_LANDMARKS = 21
NUM_COORDS = 3 # x, y, z

def normalize_landmarks(coords):
    """
    Нормализует 21 landmark (x, y, z):
    1. Перенос начала координат в запястье (индекс 0) -> wrist-relative.
    2. Масштабирование делением на максимальное расстояние от запястья -> unit-scale.
    """
    # Смещение относительно запястья (первая точка coords[0..2])
    x0, y0, z0 = coords[0], coords[1], coords[2]
    norm_coords = []
    
    for i in range(NUM_LANDMARKS):
        norm_coords.append(coords[i*3] - x0)
        norm_coords.append(coords[i*3+1] - y0)
        norm_coords.append(coords[i*3+2] - z0)
        
    # Определение максимального расстояния от запястья
    max_dist = 0.0
    for i in range(NUM_LANDMARKS):
        dist = np.sqrt(norm_coords[i*3]**2 + norm_coords[i*3+1]**2 + norm_coords[i*3+2]**2)
        if dist > max_dist:
            max_dist = dist
            
    # Масштабирование
    if max_dist > 0.0:
        for i in range(len(norm_coords)):
            norm_coords[i] /= max_dist
            
    return norm_coords

def load_data(dataset_path):
    X = []
    y = []
    
    with open(dataset_path, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        header = next(reader) # Пропускаем заголовок
        
        for row in reader:
            if not row: continue
            
            label = row[0]
            coords = []
            try:
                for i in range(1, len(row)):
                    coords.append(float(row[i]))
                
                if len(coords) == NUM_LANDMARKS * NUM_COORDS:
                    # Применяем нормализацию
                    normalized = normalize_landmarks(coords)
                    X.append(normalized)
                    y.append(label)
            except ValueError:
                continue
                
    return np.array(X), np.array(y)

def main():
    print(f"Loading data from {DATASET_PATH}...")
    try:
        X, y = load_data(DATASET_PATH)
    except FileNotFoundError:
        print(f"Error: File {DATASET_PATH} not found. Please collect data using the app first.")
        return

    if len(X) == 0:
        print("Error: Dataset is empty.")
        return

    print(f"Loaded {len(X)} samples.")
    
    # Кодирование меток
    label_encoder = LabelEncoder()
    y_encoded = label_encoder.fit_transform(y)
    classes = label_encoder.classes_
    num_classes = len(classes)
    
    print(f"Classes: {classes}")
    
    # Сохранение меток
    with open(LABELS_SAVE_PATH, 'w', encoding='utf-8') as f:
        for cls in classes:
            f.write(f"{cls}\n")
    print(f"Labels saved to {LABELS_SAVE_PATH}")
    
    # Разделение на train/test
    X_train, X_test, y_train, y_test = train_test_split(X, y_encoded, test_size=0.2, random_state=42)
    
    # Создание улучшенной модели для поддержки 968 классов
    model = tf.keras.models.Sequential([
        tf.keras.layers.Input(shape=(NUM_LANDMARKS * NUM_COORDS,)),
        tf.keras.layers.Dense(256, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(128, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ])
    
    model.compile(
        optimizer='adam',
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    # Обучение
    print("Training model...")
    model.fit(
        X_train, y_train,
        epochs=50,
        batch_size=64,
        validation_data=(X_test, y_test)
    )
    
    # Оценка
    loss, accuracy = model.evaluate(X_test, y_test)
    print(f"Test accuracy: {accuracy:.4f}")
    
    # Конвертация в TFLite
    print("Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    
    with open(MODEL_SAVE_PATH, 'wb') as f:
        f.write(tflite_model)
        
    print(f"Model saved to {MODEL_SAVE_PATH}")
    
    # Автоматическое копирование файлов в assets проекта
    assets_dir = os.path.join(os.getcwd(), 'app', 'src', 'main', 'assets')
    if os.path.exists(assets_dir):
        try:
            shutil.copy2(MODEL_SAVE_PATH, os.path.join(assets_dir, MODEL_SAVE_PATH))
            shutil.copy2(LABELS_SAVE_PATH, os.path.join(assets_dir, LABELS_SAVE_PATH))
            print(f"Successfully copied model and labels to: {assets_dir}")
        except Exception as e:
            print(f"Error copying files to assets: {e}")
            
    print("\nInstructions:")
    print(f"1. Copy '{MODEL_SAVE_PATH}' and '{LABELS_SAVE_PATH}' to your device.")
    print("2. Place them in: Android/data/com.matrixsign/files/")
    print("3. Restart the app.")

if __name__ == '__main__':
    main()

