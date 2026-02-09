"""
TensorRT 엔진 내보내기 스크립트

이 스크립트는 YOLOv8 PyTorch 모델(.pt)을 TensorRT 최적화 엔진(.engine)으로 변환합니다.
Jetson과 같은 엣지 디바이스에 배포하기 전, GPU가 장착된 개발 PC에서 실행하도록 설계되었습니다.

사용법:
    python export_tensorrt.py --model yolov8n-pose.pt --device 0 --imgsz 640
"""

import os
import sys
import argparse
import logging
import time
from pathlib import Path
from typing import Optional, Union

from ultralytics import YOLO
import torch


logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger("TensorRT-Exporter")


def setup_args():
    """명령줄 인자를 파싱합니다."""
    parser = argparse.ArgumentParser(
        description="TensorRT를 사용하여 YOLOv8 모델 최적화",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )
    
    
    parser.add_argument("--model", type=str, default="yolov8n-pose.pt", help="PyTorch 모델 경로 (.pt)")
    parser.add_argument("--imgsz", type=int, default=640, help="이미지 크기 (픽셀)")
    
    
    parser.add_argument("--device", type=str, default="0", help="GPU 장치 인덱스 또는 'cpu'")
    parser.add_argument("--half", action="store_true", default=True, help="FP16 양자화 사용")
    parser.add_argument("--int8", action="store_true", help="INT8 양자화 사용 (캘리브레이션 필요)")
    parser.add_argument("--workspace", type=int, default=4, help="TensorRT 워크스페이스 크기 (GB)")
    parser.add_argument("--opset", type=int, default=None, help="ONNX opset 버전")
    parser.add_argument("--simplify", action="store_true", default=True, help="ONNX 모델 단순화")
    
    
    parser.add_argument("--verify", action="store_true", help="내보내기 후 엔진 검증")
    parser.add_argument("--test-image", type=str, help="추론 테스트를 위한 선택적 이미지 경로")
    
    
    parser.add_argument("--fp32", action="store_true", help="FP16 비활성화 (FP32 사용)")
    
    return parser.parse_args()


def export_to_tensorrt(
    model_path: Union[str, Path],
    imgsz: int = 640,
    device: str = "0",
    half: bool = True,
    int8: bool = False,
    workspace: int = 4,
    opset: Optional[int] = None,
    simplify: bool = True
) -> Path:
    """
    YOLOv8 모델을 TensorRT 형식으로 내보냅니다.
    
    Returns:
        Path: 생성된 .engine 파일의 경로
    """
    model_path = Path(model_path)
    if not model_path.exists():
        logger.error(f"모델 파일을 찾을 수 없습니다: {model_path}")
        raise FileNotFoundError(model_path)

    logger.info(f"🚀 모델 내보내기 시작: {model_path.name}")
    logger.info(f"대상 설정: imgsz={imgsz}, device={device}, half={half}, int8={int8}")

    
    if device != 'cpu' and not torch.cuda.is_available():
        logger.warning("CUDA를 사용할 수 없습니다. CPU에서 내보내면 TensorRT 엔진이 생성되지 않거나 실패할 수 있습니다.")
        device = 'cpu'

    start_time = time.time()
    
    try:
        model = YOLO(str(model_path))
        
        
        
        
        engine_path_str = model.export(
            format="engine",
            imgsz=imgsz,
            device=device,
            half=half,
            int8=int8,
            workspace=workspace,
            opset=opset,
            simplify=simplify,
            verbose=False 
        )
        
        engine_path = Path(engine_path_str)
        elapsed = time.time() - start_time
        
        
        print(f"\n{'='*70}")
        logger.info(f"✅ 내보내기 성공! (소요 시간: {elapsed:.1f}초)")
        print(f"{'='*70}")
        print(f"  • 소스 모델   : {model_path}")
        print(f"  • 타겟 엔진   : {engine_path}")
        print(f"  • 엔진 크기   : {engine_path.stat().st_size / (1024*1024):.2f} MB")
        print(f"  • 해상도      : {imgsz}x{imgsz}")
        print(f"  • 정밀도      : {'INT8' if int8 else ('FP16' if half else 'FP32')}")
        print(f"{'='*70}")
        
        print("\n배포를 위한 다음 단계:")
        print(f"  1. '{engine_path.name}' 파일을 엣지 디바이스(Jetson)로 복사하세요.")
        print(f"  2. 'app/config.py' 수정: MODEL_PATH = \"{engine_path.name}\"")
        print(f"  3. 실행: python -m app.main\n")
        
        return engine_path

    except Exception as e:
        logger.error(f"❌ 내보내기 실패: {e}")
        raise


def verify_engine(engine_path: Union[str, Path], test_image: Optional[str] = None):
    """내보낸 엔진이 성공적으로 로드되고 추론을 수행하는지 확인합니다."""
    engine_path = Path(engine_path)
    logger.info(f"🔍 TensorRT 엔진 검증 중: {engine_path.name}")
    
    try:
        
        model = YOLO(str(engine_path), task='pose')
        logger.info("  - 엔진 로드 성공")
        
        if test_image and Path(test_image).exists():
            logger.info(f"  - 테스트 이미지 추론 실행: {test_image}")
            results = model.predict(test_image, verbose=False)
            
            det_count = len(results[0].boxes) if results[0].boxes is not None else 0
            kp_count = len(results[0].keypoints.xy) if results[0].keypoints is not None else 0
            
            logger.info(f"  - 추론 결과: 탐지 {det_count}개, 키포인트 {kp_count}세트")
            logger.info("  - 상태: 통과(PASS)")
        else:
            if test_image:
                logger.warning(f"  - 테스트 이미지 {test_image}를 찾을 수 없습니다. 추론 테스트를 건너뜁니다.")
            logger.info("  - 엔진 검증 상태: 통과(로드 전용)")
            
    except Exception as e:
        logger.error(f"❌ 검증 실패: {e}")
        raise


def main():
    args = setup_args()
    
    
    if args.fp32:
        args.half = False
    
    try:
        engine_path = export_to_tensorrt(
            model_path=args.model,
            imgsz=args.imgsz,
            device=args.device,
            half=args.half,
            int8=args.int8,
            workspace=args.workspace,
            opset=args.opset,
            simplify=args.simplify
        )
        
        if args.verify or args.test_image:
            verify_engine(engine_path, args.test_image)
            
    except KeyboardInterrupt:
        logger.info("\n사용자에 의해 취소되었습니다.")
        return 1
    except Exception:
        
        return 1
        
    return 0


if __name__ == "__main__":
    sys.exit(main())


