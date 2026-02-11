import http from "k6/http";
import { check, sleep } from "k6";

// 헬스 데이터 대량 업로드 시나리오
// 100명의 피부양자 기기가 동시에 데이터를 전송하는 상황 가정
export let options = {
  stages: [
    { duration: "30s", target: 50 }, // 30초 동안 50명까지 증가
    { duration: "1m", target: 100 }, // 1분 동안 100명 유지
    { duration: "30s", target: 0 }, // 30초 동안 종료
  ],
  thresholds: {
    http_req_duration: ["p(95)<500"], // 95%의 요청이 500ms 이내여야 함
  },
};

const BASE_URL = "http://host.docker.internal:8080/api/health";

export default function () {
  const groupId = Math.floor(Math.random() * 100) + 1; // 1~100 사이의 groupId 시뮬레이션

  const payload = JSON.stringify([
    {
      type: "HEART_RATE",
      value: Math.floor(Math.random() * (100 - 60 + 1)) + 60, // 60~100 사이 심박수
      timestamp: new Date().toISOString(),
    },
    {
      type: "STEP_COUNT",
      value: Math.floor(Math.random() * 100),
      timestamp: new Date().toISOString(),
    },
  ]);

  const params = {
    headers: {
      "Content-Type": "application/json",
      // JWT 인증이 필요한 경우 여기에 추가
      // 'Authorization': `Bearer ${__ENV.MY_TOKEN}`,
    },
  };

  let res = http.post(`${BASE_URL}/data?groupId=${groupId}`, payload, params);

  check(res, {
    "upload status is 200": (r) => r.status === 200,
  });

  sleep(Math.random() * 2 + 1); // 1~3초 간격으로 전송 시뮬레이션
}
