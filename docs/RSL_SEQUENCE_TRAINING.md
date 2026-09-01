# RSL Sequence Classifier Training Guide

## Overview

This document describes how to train a proper 1D-CNN sequence classifier for Russian Sign Language (RSL) isolated word recognition.

**Current State (Sprint 1):** The app uses a baseline implementation that applies temporal smoothing over a single-frame classifier. This works for 13 words that have training data but is not a true sequence model.

**Target State:** A 1D-CNN trained on landmark sequences that can recognize all 36 dictionary words.

## Architecture

### Input
- **Shape:** `[batch, sequence_length, features]` = `[1, 15, 63]`
  - `sequence_length = 15`: Number of frames (0.5 seconds @ 30fps)
  - `features = 63`: 21 hand landmarks × 3 coordinates (x, y, z)
- **Preprocessing:** 
  - Wrist-relative normalization (subtract wrist position from all landmarks)
  - Unit-scale normalization (divide by max distance from wrist)

### Model Architecture (Recommended)

```python
Input: [batch, 15, 63]
    ↓
Conv1D(64 filters, kernel=3, activation=ReLU)
    ↓
BatchNorm + Dropout(0.3)
    ↓
Conv1D(128 filters, kernel=3, activation=ReLU)
    ↓
BatchNorm + Dropout(0.3)
    ↓
GlobalMaxPooling1D
    ↓
Dense(128, activation=ReLU)
    ↓
Dropout(0.5)
    ↓
Dense(num_classes, activation=Softmax)
```

### Output
- **Shape:** `[batch, num_classes]` = `[1, 13]` (current) or `[1, 36]` (target)
- **Classes:** Words from `app/src/main/assets/rsl_dictionary.json`

## Data Collection Requirements

### Current Status
- ✓ **13 words** have training data in the 968-class Slovo dataset
- ✗ **23 words** need new data collection

See `app/src/main/assets/rsl_dictionary_label_mapping.json` for the complete list.

### Data Collection Protocol

For each missing word, collect:
- **Minimum:** 50 video sequences (5-10 different signers × 5-10 repetitions each)
- **Duration:** 0.5-2.0 seconds per sequence (user holds/performs sign)
- **Format:** 
  - Video: 30fps, front-facing camera
  - Extract MediaPipe landmarks offline
  - Save as: `{word}/{signer_id}/{sequence_id}.npy` (63 floats × num_frames)

### Data Augmentation
- **Temporal:** Random crop/pad within 10-30 frames
- **Spatial:** Small random rotation/scale (±5%)
- **Speed:** Time warping (0.8x - 1.2x speed)

## Training Procedure

### 1. Prepare Dataset

```python
# Example: Load landmarks from videos
import mediapipe as mp
import numpy as np

def extract_landmarks_from_video(video_path):
    """Extract hand landmarks from video using MediaPipe."""
    mp_hands = mp.solutions.hands.Hands(
        static_image_mode=False,
        max_num_hands=1,
        min_detection_confidence=0.5
    )
    
    # Process video frames
    # Extract landmarks
    # Normalize (wrist-relative + unit-scale)
    # Return: np.array of shape [num_frames, 63]
    pass

# Organize dataset
dataset = []
for word in target_vocabulary:
    for video_file in get_videos(word):
        landmarks = extract_landmarks_from_video(video_file)
        dataset.append({
            'landmarks': landmarks,
            'label': word
        })
```

### 2. Build and Train Model

```python
import tensorflow as tf

def build_sequence_model(sequence_length=15, num_features=63, num_classes=36):
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(sequence_length, num_features)),
        
        tf.keras.layers.Conv1D(64, kernel_size=3, activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        
        tf.keras.layers.Conv1D(128, kernel_size=3, activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.Dropout(0.3),
        
        tf.keras.layers.GlobalMaxPooling1D(),
        
        tf.keras.layers.Dense(128, activation='relu'),
        tf.keras.layers.Dropout(0.5),
        
        tf.keras.layers.Dense(num_classes, activation='softmax')
    ])
    
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    return model

# Train
model = build_sequence_model(num_classes=13)  # Start with 13 words
model.fit(train_dataset, validation_data=val_dataset, epochs=50)
```

### 3. Convert to TFLite

```python
# Convert to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

# Save
with open('rsl_sequence_classifier.tflite', 'wb') as f:
    f.write(tflite_model)

# Save vocabulary labels
with open('rsl_sequence_labels.txt', 'w', encoding='utf-8') as f:
    for word in vocabulary:
        f.write(word + '\n')
```

### 4. Deploy to App

1. Place `rsl_sequence_classifier.tflite` in `app/src/main/assets/`
2. Place `rsl_sequence_labels.txt` in `app/src/main/assets/`
3. Update `RslSequenceClassifier.kt` to:
   - Load the new model
   - Use proper sequence inference (not single-frame baseline)
   - Update input shape to `[1, 15, 63]`

## Testing

### Offline Testing
```bash
# Test model on held-out sequences
python test_sequence_model.py --model rsl_sequence_classifier.tflite --test_data test_sequences/
```

### On-Device Testing
1. Build and install app with new model
2. Use hold-to-sign gesture
3. Verify:
   - Buffer fills to 15 frames (~0.5s)
   - Correct word appears
   - Confidence > 0.5
   - Only 13 (or 36) vocabulary words can be recognized

## Evaluation Metrics

- **Per-word accuracy:** > 90% for all words
- **Confusion matrix:** Check for systematic errors
- **Inference latency:** < 50ms on mid-range device
- **Model size:** < 5MB

## Current Limitations

Sprint 1 baseline uses:
- ❌ Single-frame classifier (not sequence)
- ❌ Only 13 words (missing 23)
- ✓ Sequence buffering (ready for true CNN)
- ✓ Wrist-relative + unit-scale normalization
- ✓ Hold-to-sign UX

## Next Steps

1. **Immediate:** Collect video data for 23 missing words
2. **Train:** 1D-CNN with all 36 words
3. **Deploy:** Replace baseline with trained model
4. **Expand:** Add fingerspelling (dactyl) as separate model
5. **Continuous signing:** Research CTC/Transformer for non-isolated signs

## References

- MediaPipe Hands: https://developers.google.com/mediapipe/solutions/vision/hand_landmarker
- TensorFlow Lite: https://www.tensorflow.org/lite
- Sign Language Recognition Papers:
  - "Deep Sign: Enabling Robust Statistical Continuous Sign Language Recognition" (2016)
  - "Sign Language Recognition with Transformers" (2022)
