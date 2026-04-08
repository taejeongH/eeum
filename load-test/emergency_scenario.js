import http from "k6/http";
import { check, sleep } from "k6";

// 낙상 이력 조회 (응급 상황 데이터 조회 부하 테스트)
export let options = {
  stages: [
    { duration: "30s", target: 50 },
    { duration: "1m", target: 50 },
    { duration: "30s", target: 0 },
  ],
};

const BASE_URL = __ENV.BASE_URL || "https://i14a105.p.ssafy.io/api";
const TOKEN =
  "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJrYWthb180NzExODU0OTMwQHNvY2lhbC5lZXVtIiwiaWQiOjEsImF1dGgiOiJST0xFX1VTRVIifQ.NjF9MeXNw16rVcXk6M8AI7KLRqt5t_3w-OBtvj7i7PKag91G0P9Pj4_hlalNK93jym1Yv5uvzlHlLgpcXxj_Tw9o0EGsTLVRVAmv6lYccH0kCDfvJGuC2RU4jbG3mxAROzVvUmJfqat9LMyDJcTuRajWn3JOraocIguzEce_Z-_BBorvyMCgxEzA0o3lR4mAioxPb4dw50ROvycdWibUY2mAN14jY9tnBCjUK8xXlgm0FGzp8WEJBu_d57nDYWUNfyhBj2LvidrENvRW86vTAAvwRNGOWYCW1oAQtXHrWuD3U2ujr94pMNSxrSnonMBANL2NaOiichbFX4WVhGAkoQ";

export default function () {
  const params = {
    headers: { Authorization: TOKEN },
  };

  let res = http.get(`${BASE_URL}/falls/families/1`, params);

  check(res, {
    "get fall history status is 200": (r) => r.status === 200,
  });

  sleep(1);
}
