import http from "k6/http";
import { check, sleep } from "k6";

// 앨범 조회 및 업로드 준비 부하 테스트
export let options = {
  vus: 1000,
  duration: "1m",
};

const BASE_URL = __ENV.BASE_URL || "https://i14a105.p.ssafy.io/api";
const TOKEN =
  "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJrYWthb180NzExODU0OTMwQHNvY2lhbC5lZXVtIiwiaWQiOjEsImF1dGgiOiJST0xFX1VTRVIifQ.NjF9MeXNw16rVcXk6M8AI7KLRqt5t_3w-OBtvj7i7PKag91G0P9Pj4_hlalNK93jym1Yv5uvzlHlLgpcXxj_Tw9o0EGsTLVRVAmv6lYccH0kCDfvJGuC2RU4jbG3mxAROzVvUmJfqat9LMyDJcTuRajWn3JOraocIguzEce_Z-_BBorvyMCgxEzA0o3lR4mAioxPb4dw50ROvycdWibUY2mAN14jY9tnBCjUK8xXlgm0FGzp8WEJBu_d57nDYWUNfyhBj2LvidrENvRW86vTAAvwRNGOWYCW1oAQtXHrWuD3U2ujr94pMNSxrSnonMBANL2NaOiichbFX4WVhGAkoQ";

export default function () {
  const params = {
    headers: { Authorization: TOKEN },
  };

  // 1. 앨범 목록 조회
  let listRes = http.get(`${BASE_URL}/families/1/album`, params);
  check(listRes, { "get album success": (r) => r.status === 200 });

  // 2. Presigned URL 요청 (업로드 시뮬레이션 전단계)
  let urlRes = http.get(
    `${BASE_URL}/album/presigned-url?fileName=test.jpg&contentType=image/jpeg`,
    params,
  );
  check(urlRes, { "get presigned success": (r) => r.status === 200 });

  sleep(3);
}
