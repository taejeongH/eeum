import http from "k6/http";
import { check, sleep } from "k6";

// 메시지 목록 조회 부하 테스트
export let options = {
  stages: [
    { duration: "30s", target: 100 }, // 100명까지 램프업
    { duration: "1m", target: 100 }, // 100명 유지
    { duration: "30s", target: 0 }, // 램프다운
  ],
};

const BASE_URL = __ENV.BASE_URL || "https://i14a105.p.ssafy.io/api";
const TOKEN =
  "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJrYWthb180NzExODU0OTMwQHNvY2lhbC5lZXVtIiwiaWQiOjEsImF1dGgiOiJST0xFX1VTRVIifQ.NjF9MeXNw16rVcXk6M8AI7KLRqt5t_3w-OBtvj7i7PKag91G0P9Pj4_hlalNK93jym1Yv5uvzlHlLgpcXxj_Tw9o0EGsTLVRVAmv6lYccH0kCDfvJGuC2RU4jbG3mxAROzVvUmJfqat9LMyDJcTuRajWn3JOraocIguzEce_Z-_BBorvyMCgxEzA0o3lR4mAioxPb4dw50ROvycdWibUY2mAN14jY9tnBCjUK8xXlgm0FGzp8WEJBu_d57nDYWUNfyhBj2LvidrENvRW86vTAAvwRNGOWYCW1oAQtXHrWuD3U2ujr94pMNSxrSnonMBANL2NaOiichbFX4WVhGAkoQ";

export default function () {
  const params = {
    headers: {
      Authorization: TOKEN,
    },
  };

  // 1번 그룹의 메시지 목록을 가져옴 (페이지네이션 적용: 0페이지, 20개)
  let res = http.get(`${BASE_URL}/groups/1/messages?page=0&size=20`, params);

  check(res, {
    "get messages status is 200": (r) => r.status === 200,
    "has message list": (r) => Array.isArray(r.json().data),
  });

  sleep(1); // 1초 간격으로 리프레시한다고 가정
}
