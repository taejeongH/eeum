import sys
import os
from datetime import datetime
import torchaudio
from pydub import AudioSegment

# Matcha-TTS 경로 추가
sys.path.append('third_party/Matcha-TTS')
from cosyvoice.cli.cosyvoice import AutoModel

class VoiceMessageSystem:
    def __init__(self, model_dir='pretrained_models/Fun-CosyVoice3-0.5B'):
        """음성 메시지 시스템 초기화"""
        print("모델을 로딩중입니다... 잠시만 기다려주세요.")
        self.cosyvoice = AutoModel(model_dir=model_dir)
        self.voice_samples = {}
        self.output_dir = 'voice_messages'
        self.temp_dir = 'temp_converted'
        
        if not os.path.exists(self.output_dir):
            os.makedirs(self.output_dir)
        if not os.path.exists(self.temp_dir):
            os.makedirs(self.temp_dir)
        
        print("✓ 모델 로딩 완료!")
    
    def convert_to_wav(self, audio_path):
        """
        다양한 오디오 형식을 WAV로 변환합니다.
        지원 형식: m4a, mp3, aac, flac, ogg, wma 등
        
        Returns:
            변환된 WAV 파일 경로 (이미 WAV면 원본 경로 반환)
        """
        file_ext = os.path.splitext(audio_path)[1].lower()
        
        # 이미 WAV 형식이면 그대로 반환
        if file_ext == '.wav':
            return audio_path
        
        print(f"  '{file_ext}' 파일을 WAV로 변환 중...")
        
        try:
            # 파일명 생성
            filename = os.path.basename(audio_path)
            wav_filename = os.path.splitext(filename)[0] + '_converted.wav'
            wav_path = os.path.join(self.temp_dir, wav_filename)
            
            # 오디오 로드 및 변환
            audio = AudioSegment.from_file(audio_path)
            
            # WAV로 내보내기 (16kHz, mono 권장)
            audio = audio.set_frame_rate(16000).set_channels(1)
            audio.export(wav_path, format='wav')
            
            print(f"  ✓ 변환 완료: {wav_path}")
            return wav_path
            
        except Exception as e:
            print(f"  ✗ 변환 실패: {e}")
            print(f"  → ffmpeg가 설치되어 있는지 확인하세요: conda install -c conda-forge ffmpeg -y")
            raise ValueError(f"'{audio_path}' 파일을 WAV로 변환할 수 없습니다.")
    
    def register_voice_samples(self, samples_info):
        """목소리 샘플들을 등록합니다."""
        print("\n목소리 샘플을 등록중입니다...")
        for idx, sample in enumerate(samples_info, 1):
            name = sample['name']
            audio_path = sample['audio_path']
            transcript = sample['transcript']
            
            if not os.path.exists(audio_path):
                print(f"⚠ 경고: {audio_path} 파일을 찾을 수 없습니다.")
                continue
            
            # WAV로 변환 (필요한 경우)
            try:
                wav_path = self.convert_to_wav(audio_path)
                self.voice_samples[name] = {
                    'audio_path': wav_path,
                    'original_path': audio_path,
                    'transcript': transcript
                }
                print(f"  {idx}. '{name}' 샘플 등록 완료 (원본: {audio_path})")
            except Exception as e:
                print(f"  ✗ '{name}' 샘플 등록 실패: {e}")
                continue
        
        print(f"\n총 {len(self.voice_samples)}개의 목소리 샘플이 등록되었습니다.\n")
    
    def send_voice_message(self, text, sample_name=None, output_filename=None):
        """텍스트를 음성으로 변환하여 저장합니다."""
        if not self.voice_samples:
            raise ValueError("등록된 목소리 샘플이 없습니다.")
        
        if sample_name is None:
            sample_name = list(self.voice_samples.keys())[0]
        
        if sample_name not in self.voice_samples:
            raise ValueError(f"'{sample_name}' 샘플을 찾을 수 없습니다.")
        
        sample = self.voice_samples[sample_name]
        
        if output_filename is None:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            output_filename = f"message_{timestamp}.wav"
        
        output_path = os.path.join(self.output_dir, output_filename)
        
        print(f"\n음성 생성 중...")
        print(f"  텍스트: {text[:50]}{'...' if len(text) > 50 else ''}")
        print(f"  사용 샘플: {sample_name}")
        
        prompt_text = f"You are a helpful assistant.<|endofprompt|>{sample['transcript']}"
        
        try:
            # WAV로 변환된 경로 사용
            wav_audio_path = sample['audio_path']
            
            for i, output in enumerate(self.cosyvoice.inference_zero_shot(
                text,
                prompt_text,
                wav_audio_path,
                stream=False
            )):
                torchaudio.save(output_path, output['tts_speech'], self.cosyvoice.sample_rate)
                print(f"✓ 음성 메시지 생성 완료!")
                print(f"  저장 위치: {output_path}")
                return output_path
        except Exception as e:
            print(f"✗ 오류 발생: {e}")
            raise
    
    def send_multiple_messages(self, messages):
        """여러 메시지를 한 번에 생성합니다."""
        print(f"\n=== {len(messages)}개의 음성 메시지 생성 시작 ===\n")
        results = []
        
        for idx, msg in enumerate(messages, 1):
            print(f"[{idx}/{len(messages)}]")
            text = msg['text']
            sample_name = msg.get('sample_name', None)
            
            try:
                output_path = self.send_voice_message(text, sample_name)
                results.append({
                    'success': True,
                    'text': text,
                    'output_path': output_path
                })
            except Exception as e:
                results.append({
                    'success': False,
                    'text': text,
                    'error': str(e)
                })
            
            print()
        
        success_count = sum(1 for r in results if r['success'])
        print(f"\n=== 완료: {success_count}/{len(messages)}개 성공 ===")
        
        return results


if __name__ == "__main__":
    # 시스템 초기화
    system = VoiceMessageSystem()
    
    # 5개의 샘플 등록 (m4a, mp3, wav 모두 가능!)
    voice_samples = [
        {
            'name': '인사',
            'audio_path': './voice_samples/gy001.m4a',
            'transcript': '아 오늘 점심은 함박 스테이크 나왔고, 그리고 샐러드가 약간 맛있었어요. 괜찮았습니다.'
        },
        {
            'name': '일상',
            'audio_path': './voice_samples/gy002.m4a',
            'transcript': '지금 카카오 로그인 구현하고 있는데 DB는 RDS로 구현했습니다.'
        },
        {
            'name': '격려',
            'audio_path': './voice_samples/gy003.m4a',
            'transcript': '아 어제 저녁 뭐먹었지? 아 어제 저녁 아 서브웨이 먹었던것 같아요. 서브웨이 먹었습니다.'
        },
        {
            'name': '감사',
            'audio_path': './voice_samples/gy004.m4a',
            'transcript': '아 백준 문제는 하루에 한 두 문제씩 풀고 있습니다.'
        },
        {
            'name': '안부',
            'audio_path': './voice_samples/gy005.m4a',
            'transcript': '저는 발표 안하려고요. 창민님이 하세요.'
        }
    ]
    
    system.register_voice_samples(voice_samples)
    
    # 단일 메시지 전송
    print("\n" + "="*50)
    print("단일 메시지 전송 예시")
    print("="*50)
    
    system.send_voice_message(
        text="할머니! 오늘 학교에서 시험 잘 봤어요. 할머니 생각하면서 열심히 했어요!",
        sample_name='격려'
    )
    
    # 여러 메시지 한 번에 전송
    print("\n" + "="*50)
    print("여러 메시지 일괄 전송 예시")
    print("="*50)
    
    messages_to_send = [
        {
            'text': '할머니 안녕하세요! 요즘 날씨가 쌀쌀한데 감기 조심하세요.',
            'sample_name': '안부'
        },
        {
            'text': '오늘 할머니가 좋아하시는 된장찌개 해먹었어요. 할머니 생각났어요!',
            'sample_name': '일상'
        },
        {
            'text': '항상 건강하시고 오래오래 함께해요. 사랑합니다!',
            'sample_name': '감사'
        }
    ]
    
    results = system.send_multiple_messages(messages_to_send)
    
    # 결과 확인
    print("\n생성된 파일들:")
    for r in results:
        if r['success']:
            print(f"  ✓ {r['output_path']}")
        else:
            print(f"  ✗ 실패: {r['text'][:30]}... - {r['error']}")