import http from "k6/http";
import { check, sleep } from "k6";

export let options = {
  stages: [
    { duration: "30s", target: 20 }, // ramp up to 20 users
    { duration: "1m", target: 20 }, // stay at 20 users
    { duration: "30s", target: 0 }, // ramp down to 0 users
  ],
};

export default function () {
  // Replace with your actual backend API URL
  let res = http.get("http://host.docker.internal:8080/api/health");

  check(res, {
    "status is 200": (r) => r.status === 200,
  });

  sleep(1);
}
