import os
import time
import torch
import numpy as np
import cv2
from ultralytics import YOLO

def benchmark_model(model_path, num_frames=100, warm_up=10):
    print(f"\n[Benchmarking] {model_path}")
    
    
    try:
        model = YOLO(model_path)
    except Exception as e:
        print(f"Failed to load model: {e}")
        return None

    
    dummy_frame = np.zeros((1080, 1920, 3), dtype=np.uint8)
    
    
    print(f"  - Warming up ({warm_up} frames)...")
    for _ in range(warm_up):
        model.predict(dummy_frame, verbose=False, device=0)
    
    
    print(f"  - Measuring ({num_frames} frames)...")
    start_time = time.time()
    for _ in range(num_frames):
        
        model.predict(dummy_frame, verbose=False, device=0)
    end_time = time.time()
    
    total_time = end_time - start_time
    avg_latency = (total_time / num_frames) * 1000 
    fps = num_frames / total_time
    
    print(f"  - Result: Avg Latency = {avg_latency:.2f}ms, FPS = {fps:.2f}")
    
    
    del model
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
        
    return {"latency": avg_latency, "fps": fps}

def main():
    
    pt_path = os.getenv("PT_MODEL_PATH", "yolov8n-pose.pt")
    
    
    
    engine_path = os.getenv("ENGINE_MODEL_PATH", "yolov8n-pose.engine")
    
    results = {}
    
    if os.path.exists(pt_path):
        results["PyTorch (.pt)"] = benchmark_model(pt_path)
    else:
        print(f"Warning: PT model not found at {pt_path}")

    if os.path.exists(engine_path):
        results["TensorRT (.engine)"] = benchmark_model(engine_path)
    else:
        print(f"Warning: Engine model not found at {engine_path}")
        print("Tip: Run 'python export_tensorrt.py' first on Jetson.")

    if len(results) >= 2:
        print("\n" + "="*40)
        print(" PERFORMANCE COMPARISON ")
        print("="*40)
        pt_res = results.get("PyTorch (.pt)")
        tr_res = results.get("TensorRT (.engine)")
        
        if pt_res and tr_res:
            speedup = pt_res['latency'] / tr_res['latency']
            fps_gain = tr_res['fps'] - pt_res['fps']
            
            print(f"PyTorch FPS     : {pt_res['fps']:.2f}")
            print(f"TensorRT FPS    : {tr_res['fps']:.2f}")
            print(f"Speedup Factor  : {speedup:.2f}x")
            print(f"FPS Increase    : +{fps_gain:.2f}")
        print("="*40)

if __name__ == "__main__":
    main()
