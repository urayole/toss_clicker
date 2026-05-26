#!/usr/bin/env python3
import cv2
import os
import sys

def crop_template(image_path, x, y, w, h, output_path):
    """
    Crops a sub-region (template) from an image based on coordinates and saves it.
    """
    if not os.path.exists(image_path):
        print(f"Error: Base image '{image_path}' does not exist.")
        sys.exit(1)

    img = cv2.imread(image_path)
    if img is None:
        print(f"Error: Could not read image '{image_path}'")
        sys.exit(1)

    img_h, img_w = img.shape[:2]

    # Boundary check
    if x < 0 or y < 0 or x + w > img_w or y + h > img_h:
        print(f"Error: Crop region coordinates ({x}, {y}, {w}, {h}) exceed image dimensions ({img_w}x{img_h}).")
        sys.exit(1)

    # Crop
    cropped = img[y:y+h, x:x+w]
    
    # Ensure directory exists
    output_dir = os.path.dirname(output_path)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)

    cv2.imwrite(output_path, cropped)
    print(f"Success: Template cropped and saved to '{output_path}' ({w}x{h} pixels).")

if __name__ == "__main__":
    if len(sys.argv) < 6:
        print("Usage: python crop_template.py <image_path> <x> <y> <width> <height> <output_path>")
        print("Example: python crop_template.py ./frames/frame_00010.png 100 250 80 80 ./templates/pattern1.png")
    else:
        img_path = sys.argv[1]
        x = int(sys.argv[2])
        y = int(sys.argv[3])
        w = int(sys.argv[4])
        h = int(sys.argv[5])
        out_path = sys.argv[6]
        
        crop_template(img_path, x, y, w, h, out_path)
