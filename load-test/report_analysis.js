import http from "k6/http";
import { check, sleep } from "k6";

// AI 리포트 분석 요청 시나리오 (무거운 작업)
// 분석 요청이 동시에 몰릴 때 서버의 처리 능력 확인
export let options = {
  vus: 10, // 동시에 10명의 사용자가 분석 요청
  duration: "1m",
};

const BASE_URL = __ENV.BASE_URL || "https://i14a105.p.ssafy.io/api";

export default function () {
  const groupId = 1;
  const today = new Date().toISOString().split("T")[0];

  const params = {
    headers: {
      "Content-Type": "application/json",
      Authorization:
        "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJrYWthb180NzExODU0OTMwQHNvY2lhbC5lZXVtIiwiaWQiOjEsImF1dGgiOiJST0xFX1VTRVIifQ.NjF9MeXNw16rVcXk6M8AI7KLRqt5t_3w-OBtvj7i7PKag91G0P9Pj4_hlalNK93jym1Yv5uvzlHlLgpcXxj_Tw9o0EGsTLVRVAmv6lYccH0kCDfvJGuC2RU4jbG3mxAROzVvUmJfqat9LMyDJcTuRajWn3JOraocIguzEce_Z-_BBorvyMCgxEzA0o3lR4mAioxPb4dw50ROvycdWibUY2mAN14jY9tnBCjUK8xXlgm0FGzp8WEJBu_d57nDYWUNfyhBj2LvidrENvRW86vTAAvwRNGOWYCW1oAQtXHrWuD3U2ujr94pMNSxrSnonMBANL2NaOiichbFX4WVhGAkoQ",
    },
  };

  // analyze API는 POST 방식이며 groupId와 date를 쿼리 파라미터로 받음
  let res = http.post(
    `${BASE_URL}/health/analyze?groupId=${groupId}&date=${today}`,
    null,
    params,
  );

  check(res, {
    "analysis status is 200": (r) => r.status === 200,
    "has report data": (r) => r.status === 200 && r.json().data !== null,
  });

  sleep(5); // 분석은 무거운 작업이므로 긴 간격을 둠
}
