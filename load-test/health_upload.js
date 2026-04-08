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

const BASE_URL = __ENV.BASE_URL || "https://i14a105.p.ssafy.io/api";

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
      Authorization:
        "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJrYWthb180NzExODU0OTMwQHNvY2lhbC5lZXVtIiwiaWQiOjEsImF1dGgiOiJST0xFX1VTRVIifQ.NjF9MeXNw16rVcXk6M8AI7KLRqt5t_3w-OBtvj7i7PKag91G0P9Pj4_hlalNK93jym1Yv5uvzlHlLgpcXxj_Tw9o0EGsTLVRVAmv6lYccH0kCDfvJGuC2RU4jbG3mxAROzVvUmJfqat9LMyDJcTuRajWn3JOraocIguzEce_Z-_BBorvyMCgxEzA0o3lR4mAioxPb4dw50ROvycdWibUY2mAN14jY9tnBCjUK8xXlgm0FGzp8WEJBu_d57nDYWUNfyhBj2LvidrENvRW86vTAAvwRNGOWYCW1oAQtXHrWuD3U2ujr94pMNSxrSnonMBANL2NaOiichbFX4WVhGAkoQ",
    },
  };

  let res = http.post(
    `${BASE_URL}/health/data?groupId=${groupId}`,
    payload,
    params,
  );

  check(res, {
    "upload status is 200": (r) => r.status === 200,
  });

  sleep(Math.random() * 2 + 1); // 1~3초 간격으로 전송 시뮬레이션
}
