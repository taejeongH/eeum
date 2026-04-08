import http from "k6/http";
import { check, sleep } from "k6";

// 메시지 전송 부하 테스트 (Voice Styling 프로세스 트리거 전단계)
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
  const payload = JSON.stringify({
    content: "테스트 메시지입니다. 잘 들리시나요?",
    voiceStyle: "KIND",
  });

  const params = {
    headers: {
      "Content-Type": "application/json",
      Authorization: TOKEN,
    },
  };

  let res = http.post(`${BASE_URL}/groups/1/messages`, payload, params);

  check(res, {
    "send message status is 200": (r) => r.status === 200,
    "has message id": (r) => {
      try {
        return (
          r.status === 200 && r.json().data && r.json().data.id !== undefined
        );
      } catch (e) {
        return false;
      }
    },
  });

  sleep(2);
}
