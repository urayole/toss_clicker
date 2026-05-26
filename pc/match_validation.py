#!/usr/bin/env python3
import cv2
import numpy as np
import glob
import os
import sys

def validate_matching(frame_dir, template_dir, threshold=0.8, use_canny=False, low_threshold=50, high_threshold=150):
    """
    Loads templates and matches them against frames in the frame directory.
    Supports Grayscale and Canny Edge pre-processing.
    """
    # Find templates
    template_files = glob.glob(os.path.join(template_dir, "*.png")) + glob.glob(os.path.join(template_dir, "*.jpg"))
    if not template_files:
        print(f"Error: No templates found in '{template_dir}'")
        return
        
    print(f"Found {len(template_files)} templates to load.")
    
    # Load and preprocess templates
    templates = []
    for tf in template_files:
        tmpl = cv2.imread(tf)
        if tmpl is None:
            print(f"Warning: Could not read template '{tf}'")
            continue
        h, w = tmpl.shape[:2]
        gray_tmpl = cv2.cvtColor(tmpl, cv2.COLOR_BGR2GRAY)
        
        if use_canny:
            processed_tmpl = cv2.Canny(gray_tmpl, low_threshold, high_threshold)
        else:
            processed_tmpl = gray_tmpl
            
        templates.append((os.path.basename(tf), processed_tmpl, w, h))
            
    # Read frames
    frame_files = sorted(glob.glob(os.path.join(frame_dir, "*.png"))) + sorted(glob.glob(os.path.join(frame_dir, "*.jpg")))
    if not frame_files:
        print(f"Error: No frames found in '{frame_dir}'")
        return
        
    print(f"Processing {len(frame_files)} frames...")
    match_count = 0
    
    for ff in frame_files:
        frame = cv2.imread(ff)
        if frame is None:
            continue
            
        gray_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        
        if use_canny:
            processed_frame = cv2.Canny(gray_frame, low_threshold, high_threshold)
        else:
            processed_frame = gray_frame
            
        matches_found = []
        for name, tmpl, w, h in templates:
            # Match template using normalized cross-correlation
            res = cv2.matchTemplate(processed_frame, tmpl, cv2.TM_CCOEFF_NORMED)
            _, max_val, _, max_loc = cv2.minMaxLoc(res)
            
            if max_val >= threshold:
                matches_found.append((name, max_val, max_loc, w, h))
                
        if matches_found:
            match_count += 1
            print(f"Frame: {os.path.basename(ff)}")
            for name, score, loc, w, h in matches_found:
                center_x = loc[0] + w // 2
                center_y = loc[1] + h // 2
                print(f"  -> Match '{name}' | Score: {score:.4f} | Center: ({center_x}, {center_y})")

    print(f"\nValidation finished. Found matches in {match_count}/{len(frame_files)} frames.")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python match_validation.py <frame_dir> <template_dir> [threshold] [use_canny]")
        print("Example: python match_validation.py ./frames ./templates 0.8 False")
    else:
        f_dir = sys.argv[1]
        t_dir = sys.argv[2]
        thresh = float(sys.argv[3]) if len(sys.argv) > 3 else 0.8
        canny = (sys.argv[4].lower() == 'true') if len(sys.argv) > 4 else False
        validate_matching(f_dir, t_dir, thresh, canny)
