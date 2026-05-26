#!/usr/bin/env python3
import cv2
import os
import sys

def extract_frames(video_path, output_dir, interval_frames=10):
    """
    Extracts frames from a video file at regular intervals.
    """
    if not os.path.exists(video_path):
        print(f"Error: Video file '{video_path}' does not exist.")
        sys.exit(1)
    
    os.makedirs(output_dir, exist_ok=True)
    cap = cv2.VideoCapture(video_path)
    
    if not cap.isOpened():
        print(f"Error: Could not open video '{video_path}'")
        sys.exit(1)
        
    count = 0
    saved_count = 0
    
    print(f"Extracting frames from {video_path}...")
    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break
        
        if count % interval_frames == 0:
            output_file = os.path.join(output_dir, f"frame_{count:05d}.png")
            cv2.imwrite(output_file, frame)
            saved_count += 1
            
        count += 1
        
    cap.release()
    print(f"Extraction completed: Saved {saved_count} frames to '{output_dir}'.")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python extract_frames.py <video_path> <output_dir> [interval_frames]")
        print("Example: python extract_frames.py recording.mp4 ./frames 10")
    else:
        video = sys.argv[1]
        out = sys.argv[2]
        interval = int(sys.argv[3]) if len(sys.argv) > 3 else 10
        extract_frames(video, out, interval)
