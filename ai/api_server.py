import os
import sys
import yaml
import logging
import uuid
import torch
import boto3
import torchaudio
import uvicorn
from fastapi import FastAPI, HTTPException, Header, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from pydub import AudioSegment
from contextlib import asynccontextmanager

# CosyVoice 전용 YAML 패치
def super_patch_yaml():
    import yaml
    for loader in [yaml.Loader, yaml.SafeLoader, yaml.FullLoader, yaml.UnsafeLoader]:
        if not hasattr(loader, 'max_depth'):
            setattr(loader, 'max_depth', 100)
super_patch_yaml()

# 로깅 설정 (파일 및 터미널 출력)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[logging.FileHandler('logs/api.log'), logging.StreamHandler()]
)
logger = logging.getLogger(__name__)

# 경로 및 모델 설정
COSYVOICE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'CosyVoice')
sys.path.append(COSYVOICE_DIR)
sys.path.append(os.path.join(COSYVOICE_DIR, 'third_party/Matcha-TTS'))

API_KEY = "eeum_server_key"
s3_client = boto3.client('s3')
cosyvoice = None

class TtsRequest(BaseModel):
    text: str
    sample_s3_url: str
    sample_transcript: str
    user_id: int
    bucket_name: str

async def verify_api_key(x_api_key: str = Header(None)):
    if x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API Key")
    return x_api_key

@asynccontextmanager
async def lifespan(app: FastAPI):
    global cosyvoice
    from cosyvoice.cli.cosyvoice import AutoModel
    cosyvoice = AutoModel(model_dir='pretrained_models/CosyVoice-300M')
    torch.set_num_threads(os.cpu_count())
    logger.info("✅ AI Model Loaded")
    yield

app = FastAPI(lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

def download_s3(bucket, key, local):
    s3_client.download_file(bucket, key, local)

def upload_s3(local, bucket, key):
    s3_client.upload_file(local, bucket, key)
    return f"https://{bucket}.s3.ap-northeast-2.amazonaws.com/{key}"

@app.post("/api/v1/voice/tts")
async def generate_tts(request: TtsRequest, x_api_key: str = Depends(verify_api_key)):
    logger.info(f"🎙️ TTS Request: User {request.user_id}")
    t_down, t_wav, t_res = f"d_{uuid.uuid4()}", f"s_{uuid.uuid4()}.wav", f"r_{uuid.uuid4()}.wav"
    
    try:
        download_s3(request.bucket_name, request.sample_s3_url, t_down)
        
        # 오디오 전처리 (16kHz, Mono)
        audio = AudioSegment.from_file(t_down)
        audio.set_frame_rate(16000).set_channels(1).export(t_wav, format="wav")
        
        # 목소리 정체성 보존을 위한 프롬프트 구성
        prompt = f"You are a helpful assistant.<|endofprompt|>{request.sample_transcript}"
        
        # AI 추론
        outputs = cosyvoice.inference_zero_shot(request.text, prompt, t_wav, stream=False)
        
        for out in outputs:
            torchaudio.save(t_res, out['tts_speech'], cosyvoice.sample_rate)
            break
            
        url = upload_s3(t_res, request.bucket_name, f"generated/{request.user_id}/{uuid.uuid4()}.wav")
        logger.info(f"TTS Success: {url}")
        return {"status": "success", "full_url": url}

    except Exception as e:
        logger.error(f"TTS Error: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        for f in [t_down, t_wav, t_res]:
            if os.path.exists(f): os.remove(f)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8504)